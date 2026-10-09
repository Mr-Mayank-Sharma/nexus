package com.nexus.oms.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.reconciliation.*;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.kafka.KafkaProducerService;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.shopify.ShopifyClient;
import com.nexus.oms.service.shopify.ShopifyTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * T-09: Shopify → OMS fulfillment reconciliation.
 *
 * Shopify is the system of truth for fulfillment. This service:
 *  1. Tolerantly matches Shopify fulfillment lines against OMS order items (by SKU),
 *  2. Accepts the fulfillment as truth (idempotent per shopify_fulfillment_id),
 *  3. Records every decision in the nx_fulfillment_reconciliation ledger,
 *  4. Runs a periodic repair job that detects Shopify-fulfilled orders the OMS missed.
 *
 * NetSuite propagation is intentionally deferred: rows are written with
 * netsuite_sync_status = PENDING until the T-14 connector exists.
 */
@Service
public class FulfillmentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentReconciliationService.class);

    /** Emitted with the order id as key/payload, mirroring OrderService's string-literal style. */
    private static final String ORDER_FULFILLED_TOPIC = "order.fulfilled";
    private static final String SHOPIFY_SETTING_SHOP_DOMAIN = "shop_domain";

    private final FulfillmentReconciliationRepository reconciliationRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final KafkaProducerService kafkaProducerService;
    private final NxIntegrationStoreRepository storeRepository;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final ShopifyClient shopifyClient;
    private final ObjectMapper objectMapper;

    public FulfillmentReconciliationService(FulfillmentReconciliationRepository reconciliationRepository,
                                            OrderRepository orderRepository,
                                            OrderItemRepository orderItemRepository,
                                            InventoryService inventoryService,
                                            KafkaProducerService kafkaProducerService,
                                            NxIntegrationStoreRepository storeRepository,
                                            IntegrationStoreService storeService,
                                            ShopifyTokenService tokenService,
                                            ShopifyClient shopifyClient,
                                            ObjectMapper objectMapper) {
        this.reconciliationRepository = reconciliationRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
        this.kafkaProducerService = kafkaProducerService;
        this.storeRepository = storeRepository;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.shopifyClient = shopifyClient;
        this.objectMapper = objectMapper;
    }

    // ====================================================================
    // 1. TOLERANT MATCHER
    // ====================================================================

    /**
     * Matches Shopify fulfillment lines against OMS order items by SKU and derives
     * a match type + resolution. Never throws on data discrepancies — it classifies
     * them (EXACT / PARTIAL / LOCATION_CHANGE / QUANTITY_DELTA / ANOMALY).
     */
    public MatchResult matchFulfillment(NxOrder order, List<NxOrderItem> items, ShopifyFulfillment fulfillment) {
        List<FulfilledItem> accepted = new ArrayList<>();
        List<FulfilledItem> remaining = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        Set<String> matchedSkus = new HashSet<>();
        boolean locationChanged = false;
        boolean quantityDelta = false;
        boolean anomaly = false;

        for (ShopifyFulfillmentLine line : fulfillment.getLineItems()) {
            NxOrderItem item = items.stream()
                    .filter(i -> line.getSku() != null && line.getSku().equalsIgnoreCase(i.getSku()))
                    .findFirst()
                    .orElse(null);

            if (item == null) {
                anomaly = true;
                notes.add("No OMS line item for SKU: " + line.getSku());
                remaining.add(FulfilledItem.builder()
                        .sku(line.getSku())
                        .quantity(line.getQuantity())
                        .location(line.getLocationId())
                        .build());
                continue;
            }
            matchedSkus.add(item.getSku());

            int openQty = item.getQuantity() - nz(item.getFulfilledQty());
            if (openQty <= 0) {
                continue; // already fully fulfilled by an earlier acceptance
            }

            int fulfilledQty = line.getQuantity() == null ? 0 : line.getQuantity();
            if (fulfilledQty > openQty) {
                notes.add("Over-fulfillment for SKU " + item.getSku()
                        + " (fulfilled " + fulfilledQty + ", open " + openQty + ") — accepting open qty");
                fulfilledQty = openQty;
                quantityDelta = true;
            } else if (fulfilledQty < openQty) {
                quantityDelta = true;
            }
            if (fulfilledQty <= 0) {
                continue;
            }

            if (line.getLocationId() != null && item.getAllocatedNodeId() != null
                    && !line.getLocationId().equals(item.getAllocatedNodeId().toString())) {
                locationChanged = true;
                notes.add("Fulfilled at " + line.getLocationId()
                        + " vs routed node " + item.getAllocatedNodeId() + " — accepted as truth");
            }

            accepted.add(FulfilledItem.builder()
                    .orderItemId(item.getId())
                    .sku(item.getSku())
                    .quantity(fulfilledQty)
                    .location(line.getLocationId())
                    .nodeId(item.getAllocatedNodeId())
                    .build());
        }

        // items still open after this fulfillment (the "remaining" side of a partial)
        for (NxOrderItem item : items) {
            int openQty = item.getQuantity() - nz(item.getFulfilledQty());
            if (openQty > 0 && !matchedSkus.contains(item.getSku())) {
                remaining.add(FulfilledItem.builder()
                        .orderItemId(item.getId())
                        .sku(item.getSku())
                        .quantity(openQty)
                        .nodeId(item.getAllocatedNodeId())
                        .build());
            }
        }

        MatchType matchType;
        Resolution resolution;
        if (anomaly || accepted.isEmpty()) {
            matchType = MatchType.ANOMALY;
            resolution = Resolution.FLAGGED;
            if (notes.isEmpty()) {
                notes.add("No matchable fulfillment lines — manual review required");
            }
        } else if (locationChanged) {
            matchType = MatchType.LOCATION_CHANGE;
            resolution = fullyFulfilled(items, accepted) ? Resolution.ACCEPTED : Resolution.PARTIAL_ACCEPTED;
        } else if (quantityDelta) {
            matchType = MatchType.QUANTITY_DELTA;
            resolution = Resolution.PARTIAL_ACCEPTED;
        } else if (fullyFulfilled(items, accepted)) {
            matchType = MatchType.EXACT;
            resolution = Resolution.ACCEPTED;
        } else {
            matchType = MatchType.PARTIAL;
            resolution = Resolution.PARTIAL_ACCEPTED;
        }

        return MatchResult.builder()
                .matchType(matchType)
                .resolution(resolution)
                .acceptedItems(accepted)
                .remainingItems(remaining)
                .notes(String.join("; ", notes))
                .build();
    }

    // ====================================================================
    // 2. TRUTH ACCEPTANCE
    // ====================================================================

    /**
     * Accepts a Shopify fulfillment as truth. Idempotent per shopify_fulfillment_id:
     * re-posting the same fulfillment returns the original result and never double-applies.
     *
     * Applies: fulfilled_qty on matched items, order status FULFILLED when complete,
     * inventory deduction at the allocated node (shared pool fallback), ledger row,
     * and an order.fulfilled Kafka event. FLAGGED matches are recorded but never mutate state.
     */
    @Transactional
    public ReconciliationResult acceptFulfillment(UUID orderId, ShopifyFulfillment fulfillment) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        if (fulfillment.getShopifyFulfillmentId() == null || fulfillment.getShopifyFulfillmentId().isBlank()) {
            throw new BadRequestException("shopifyFulfillmentId is required for idempotent acceptance");
        }

        Optional<NxFulfillmentReconciliation> existing = reconciliationRepository
                .findByTenantIdAndShopifyFulfillmentId(tenantId, fulfillment.getShopifyFulfillmentId());
        if (existing.isPresent()) {
            log.info("Fulfillment {} already accepted — idempotent skip", fulfillment.getShopifyFulfillmentId());
            return toResult(existing.get());
        }

        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (order.getTenantId() == null || !tenantId.equals(order.getTenantId())) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        List<NxOrderItem> items = orderItemRepository.findByOrderId(orderId);
        MatchResult match = matchFulfillment(order, items, fulfillment);

        if (match.getResolution() == Resolution.FLAGGED) {
            NxFulfillmentReconciliation record = buildRecord(order, fulfillment, match, 0);
            reconciliationRepository.save(record);
            log.warn("Fulfillment {} flagged for manual review (order {})", fulfillment.getShopifyFulfillmentId(), orderId);
            return toResult(record);
        }

        int inventoryDelta = 0;
        List<String> inventoryNotes = new ArrayList<>();
        for (FulfilledItem fi : match.getAcceptedItems()) {
            NxOrderItem item = items.stream()
                    .filter(i -> i.getId().equals(fi.getOrderItemId()))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                continue;
            }

            item.setFulfilledQty(nz(item.getFulfilledQty()) + fi.getQuantity());
            orderItemRepository.save(item);

            try {
                if (item.getAllocatedNodeId() != null) {
                    inventoryService.adjustInventoryBySkuAtNode(tenantId, item.getSku(), item.getAllocatedNodeId(), -fi.getQuantity());
                } else {
                    inventoryService.adjustInventoryBySku(tenantId, item.getSku(), -fi.getQuantity());
                }
                inventoryDelta -= fi.getQuantity();
            } catch (Exception e) {
                // Truth-acceptance must not be blocked by inventory edge cases — defer and record.
                log.warn("Inventory adjustment deferred for SKU {} (order {}): {}", item.getSku(), orderId, e.getMessage());
                inventoryNotes.add("INVENTORY_ADJUST_PENDING:" + item.getSku());
            }
        }

        boolean allFulfilled = items.stream().allMatch(i -> nz(i.getFulfilledQty()) >= i.getQuantity());
        if (allFulfilled && !"CANCELLED".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("FULFILLED");
            order.setSubStatus("FULFILLED");
            if (order.getShippedAt() == null) {
                order.setShippedAt(LocalDateTime.now());
            }
            orderRepository.save(order);
        }

        NxFulfillmentReconciliation record = buildRecord(order, fulfillment, match, inventoryDelta);
        if (!inventoryNotes.isEmpty()) {
            record.setNotes(record.getNotes() == null || record.getNotes().isBlank()
                    ? String.join("; ", inventoryNotes)
                    : record.getNotes() + "; " + String.join("; ", inventoryNotes));
        }
        reconciliationRepository.save(record);

        kafkaProducerService.publish(ORDER_FULFILLED_TOPIC, order.getId().toString());

        return toResult(record);
    }

    // ====================================================================
    // 3. WEBHOOK ENTRY (orders/fulfilled)
    // ====================================================================

    /**
     * Entry point for Shopify orders/fulfilled webhooks. Bypasses order-id dedup
     * (a second partial fulfillment on the same order must not be skipped) and relies
     * on per-fulfillment idempotency instead. Called with a tenant context override.
     */
    @Transactional
    public void handleFulfillmentWebhook(NxIntegrationStore store, Map<String, Object> payload) {
        TenantContext.setCurrentTenantId(store.getTenantId());
        try {
            JsonNode root = objectMapper.valueToTree(payload);
            if (!root.has("id")) {
                return;
            }
            String shopifyOrderId = root.get("id").asText();

            Optional<NxOrder> orderOpt = orderRepository
                    .findByTenantIdAndChannelOrderId(store.getTenantId(), shopifyOrderId);
            if (orderOpt.isEmpty()) {
                log.info("Fulfillment webhook for unknown order {} — left to order import", shopifyOrderId);
                return;
            }

            JsonNode fulfillments = root.get("fulfillments");
            if (fulfillments == null || !fulfillments.isArray()) {
                return;
            }

            for (JsonNode f : fulfillments) {
                ShopifyFulfillment fulfillment = parseFulfillment(f);
                if ("cancelled".equalsIgnoreCase(fulfillment.getStatus())) {
                    continue;
                }
                if (fulfillment.getLineItems() == null || fulfillment.getLineItems().isEmpty()) {
                    continue;
                }
                acceptFulfillment(orderOpt.get().getId(), fulfillment);
            }
        } finally {
            TenantContext.clear();
        }
    }

    // ====================================================================
    // 4. CROSS-SYSTEM REPAIR (SCHEDULED)
    // ====================================================================

    /**
     * Periodic repair: pulls recently fulfilled Shopify orders per store and accepts any
     * fulfillment the OMS hasn't recorded yet. Runs in the tenant context of each store.
     */
    @Scheduled(cron = "${nexus.reconciliation.cron:0 */15 * * * *}")
    @SchedulerLock(name = "FulfillmentReconciliationService.repairCrossSystemMismatches", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void repairCrossSystemMismatches() {
        List<NxIntegrationStore> stores = storeRepository.findAll().stream()
                .filter(this::isShopifyStore)
                .toList();

        for (NxIntegrationStore store : stores) {
            try {
                repairStore(store);
            } catch (Exception e) {
                log.error("Repair pass failed for store {}: {}", store.getStoreCode(), e.getMessage());
            }
        }
    }

    /** On-demand trigger exposed via the controller. */
    public void triggerRepair() {
        repairCrossSystemMismatches();
    }

    private boolean isShopifyStore(NxIntegrationStore store) {
        String shopDomain = storeService.getSetting(store.getId(), SHOPIFY_SETTING_SHOP_DOMAIN);
        return shopDomain != null && !shopDomain.isBlank();
    }

    private void repairStore(NxIntegrationStore store) {
        String shopDomain = storeService.getSetting(store.getId(), SHOPIFY_SETTING_SHOP_DOMAIN);
        String accessToken = tokenService.getAccessToken(store.getId());
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        JsonNode orders = shopifyClient.getOrders(shopDomain, accessToken,
                Map.of("status", "fulfilled", "limit", "50"));
        if (orders == null || !orders.has("orders") || !orders.get("orders").isArray()) {
            return;
        }

        int accepted = 0;
        for (JsonNode shopifyOrder : orders.get("orders")) {
            String externalId = null;
            try {
                if (!shopifyOrder.has("id")) {
                    continue;
                }
                externalId = shopifyOrder.get("id").asText();

                Optional<NxOrder> orderOpt = orderRepository
                        .findByTenantIdAndChannelOrderId(store.getTenantId(), externalId);
                if (orderOpt.isEmpty()) {
                    continue;
                }
                NxOrder order = orderOpt.get();
                if ("FULFILLED".equalsIgnoreCase(order.getStatus())) {
                    continue;
                }

                JsonNode fulfillments = shopifyOrder.get("fulfillments");
                if (fulfillments == null || !fulfillments.isArray() || fulfillments.isEmpty()) {
                    continue;
                }

                TenantContext.setCurrentTenantId(store.getTenantId());
                try {
                    for (JsonNode f : fulfillments) {
                        ShopifyFulfillment fulfillment = parseFulfillment(f);
                        if ("cancelled".equalsIgnoreCase(fulfillment.getStatus())) {
                            continue;
                        }
                        if (fulfillment.getLineItems() == null || fulfillment.getLineItems().isEmpty()) {
                            continue;
                        }
                        ReconciliationResult result = acceptFulfillment(order.getId(), fulfillment);
                        if (result.getResolution() == Resolution.ACCEPTED
                                || result.getResolution() == Resolution.PARTIAL_ACCEPTED) {
                            accepted++;
                        }
                    }
                } finally {
                    TenantContext.clear();
                }
            } catch (Exception e) {
                log.warn("Repair failed for Shopify order {}: {}", externalId, e.getMessage());
            }
        }
        log.info("Repair pass [{}]: accepted {} fulfillment(s)", store.getStoreCode(), accepted);
    }

    // ====================================================================
    // 5. REPORT & HISTORY
    // ====================================================================

    /** Ledger-based mismatch report (FLAGGED / REPAIRED rows). */
    public List<ReconciliationReportRow> getMismatchReport(UUID tenantId) {
        List<String> flagged = List.of(Resolution.FLAGGED.name(), Resolution.REPAIRED.name());
        return reconciliationRepository
                .findByTenantIdAndResolutionInOrderByCreatedAtDesc(tenantId, flagged)
                .stream()
                .map(r -> ReconciliationReportRow.builder()
                        .orderId(r.getOrderId())
                        .orderNumber(r.getOrderNumber())
                        .matchType(r.getMatchType())
                        .resolution(r.getResolution())
                        .reason(r.getNotes())
                        .netsuiteSyncStatus(r.getNetsuiteSyncStatus())
                        .inventoryDelta(r.getInventoryDelta())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();
    }

    /** Full reconciliation history for a single order. */
    public List<NxFulfillmentReconciliation> getOrderHistory(UUID tenantId, UUID orderId) {
        return reconciliationRepository.findByTenantIdAndOrderIdOrderByCreatedAtDesc(tenantId, orderId);
    }

    // ====================================================================
    // HELPERS
    // ====================================================================

    private NxFulfillmentReconciliation buildRecord(NxOrder order, ShopifyFulfillment fulfillment,
                                                    MatchResult match, int inventoryDelta) {
        return NxFulfillmentReconciliation.builder()
                .tenantId(order.getTenantId())
                .orderId(order.getId())
                .orderNumber(order.getChannelOrderId() != null ? order.getChannelOrderId() : order.getExternalId())
                .shopifyFulfillmentId(fulfillment.getShopifyFulfillmentId())
                .matchType(match.getMatchType().name())
                .resolution(match.getResolution().name())
                .fulfilledLocation(firstLocation(match.getAcceptedItems()))
                .routedLocation(order.getShipFrom())
                .fulfilledItems(toJson(match.getAcceptedItems()))
                .remainingItems(toJson(match.getRemainingItems()))
                .netsuiteSyncStatus("PENDING")
                .inventoryDelta(inventoryDelta)
                .notes(match.getNotes())
                .build();
    }

    private ReconciliationResult toResult(NxFulfillmentReconciliation record) {
        return ReconciliationResult.builder()
                .reconciliationId(record.getId())
                .orderId(record.getOrderId())
                .orderNumber(record.getOrderNumber())
                .matchType(MatchType.valueOf(record.getMatchType()))
                .resolution(Resolution.valueOf(record.getResolution()))
                .inventoryDelta(record.getInventoryDelta())
                .notes(record.getNotes())
                .build();
    }

    private ShopifyFulfillment parseFulfillment(JsonNode f) {
        String locationId = f.has("location_id") ? f.get("location_id").asText() : null;

        List<ShopifyFulfillmentLine> lines = new ArrayList<>();
        if (f.has("line_items") && f.get("line_items").isArray()) {
            for (JsonNode li : f.get("line_items")) {
                lines.add(ShopifyFulfillmentLine.builder()
                        .lineItemId(li.has("id") ? li.get("id").asText() : null)
                        .sku(li.has("sku") ? li.get("sku").asText() : null)
                        .quantity(li.has("quantity") ? li.get("quantity").asInt() : 0)
                        .locationId(locationId)
                        .build());
            }
        }

        return ShopifyFulfillment.builder()
                .shopifyFulfillmentId(f.has("id") ? f.get("id").asText() : null)
                .status(f.has("status") ? f.get("status").asText() : "fulfilled")
                .createdAt(f.has("created_at") ? f.get("created_at").asText() : null)
                .lineItems(lines)
                .build();
    }

    private boolean fullyFulfilled(List<NxOrderItem> items, List<FulfilledItem> accepted) {
        Map<UUID, Integer> acceptedByItem = new HashMap<>();
        for (FulfilledItem f : accepted) {
            acceptedByItem.merge(f.getOrderItemId(), f.getQuantity(), Integer::sum);
        }
        for (NxOrderItem item : items) {
            int total = nz(item.getFulfilledQty()) + acceptedByItem.getOrDefault(item.getId(), 0);
            if (total < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private String firstLocation(List<FulfilledItem> accepted) {
        return accepted.stream()
                .map(FulfilledItem::getLocation)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }
}