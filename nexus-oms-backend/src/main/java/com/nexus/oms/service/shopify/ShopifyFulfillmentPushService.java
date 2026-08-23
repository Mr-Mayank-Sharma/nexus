package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.IntegrationStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShopifyFulfillmentPushService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyFulfillmentPushService.class);

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    public ShopifyFulfillmentPushService(ShopifyClient shopifyClient,
                                          IntegrationStoreService storeService,
                                          ShopifyTokenService tokenService,
                                          NxIntegrationStoreRepository storeRepository,
                                          NxIntegrationSyncConfigRepository syncConfigRepository,
                                          NxSyncLogRepository syncLogRepository,
                                          OrderRepository orderRepository,
                                          ShipmentRepository shipmentRepository) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.storeRepository = storeRepository;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional
    public SyncResult pushFulfillments(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = tokenService.getAccessToken(storeId);

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(store.getTenantId())
                .integrationType("SHOPIFY_" + store.getStoreCode())
                .syncType("FULFILLMENT_PUSH")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;

        try {
            List<NxOrder> shippedOrders = orderRepository
                    .findByTenantIdAndStatus(store.getTenantId(), "SHIPPED", org.springframework.data.domain.Pageable.unpaged())
                    .getContent();

            for (NxOrder order : shippedOrders) {
                if (!"SHOPIFY".equals(order.getChannel())) continue;
                if (order.getExternalId() == null) continue;

                try {
                    long shopifyOrderId = Long.parseLong(order.getExternalId());
                    List<NxShipment> shipments = shipmentRepository.findByOrderId(order.getId());

                    // Guard: skip if Shopify already reports the order fulfilled
                    try {
                        JsonNode shopifyOrder = shopifyClient.getOrderById(shopDomain, accessToken, shopifyOrderId);
                        JsonNode orderNode = shopifyOrder != null ? shopifyOrder.get("order") : null;
                        if (orderNode != null && "fulfilled".equals(orderNode.path("fulfillment_status").asText(null))) {
                            log.info("Order {} already fulfilled in Shopify, skipping", shopifyOrderId);
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch Shopify order {}: {}", shopifyOrderId, e.getMessage());
                    }

                    if (shipments.isEmpty()) {
                        // No shipment record — fall back to tracking captured on the order itself
                        if (order.getTrackingNumber() == null) continue;
                        pushFulfillment(shopDomain, accessToken, shopifyOrderId,
                                order.getTrackingNumber(), order.getLabelUrl(), order.getCarrierId());
                        succeeded++;
                    } else {
                        for (NxShipment shipment : shipments) {
                            if (shipment.getTrackingNumber() == null) continue;
                            pushFulfillment(shopDomain, accessToken, shopifyOrderId,
                                    shipment.getTrackingNumber(), shipment.getLabelUrl(), shipment.getCarrierId());
                            succeeded++;
                        }
                    }
                    processed++;
                } catch (Exception e) {
                    log.error("Fulfillment push failed for order {}: {}", order.getId(), e.getMessage(), e);
                    failed++;
                    processed++;
                }
            }

            updateSyncConfig(storeId, "FULFILLMENT_PUSH", "COMPLETED", processed, succeeded, failed, null);
            syncLog.setStatus("COMPLETED");
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSucceeded(succeeded);
            syncLog.setItemsFailed(failed);
            syncLogRepository.save(syncLog);

        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSucceeded(succeeded);
            syncLog.setItemsFailed(failed);
            syncLog.setErrorMessage(e.getMessage());
            syncLogRepository.save(syncLog);
        }

        return SyncResult.builder()
                .syncLogId(syncLog.getId())
                .syncType("FULFILLMENT_PUSH")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .build();
    }

    private void pushFulfillment(String shopDomain, String accessToken, long shopifyOrderId,
                                 String trackingNumber, String trackingUrl, String carrierId) {
        JsonNode foResponse = shopifyClient.getOrderFulfillmentOrders(shopDomain, accessToken, shopifyOrderId);
        JsonNode fulfillmentOrders = foResponse != null ? foResponse.get("fulfillment_orders") : null;
        if (fulfillmentOrders == null || !fulfillmentOrders.isArray() || fulfillmentOrders.isEmpty()) {
            throw new BadRequestException("No fulfillment orders found for Shopify order " + shopifyOrderId);
        }

        List<Map<String, Object>> byFulfillmentOrder = new ArrayList<>();
        for (JsonNode fo : fulfillmentOrders) {
            if (!"open".equals(fo.path("status").asText())) continue;

            Map<String, Object> entry = new HashMap<>();
            entry.put("fulfillment_order_id", fo.get("id").asLong());

            JsonNode foLineItems = fo.get("line_items");
            List<Map<String, Object>> foItems = new ArrayList<>();
            if (foLineItems != null && foLineItems.isArray()) {
                for (JsonNode li : foLineItems) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", li.get("id").asLong());
                    m.put("quantity", li.has("quantity") ? li.get("quantity").asInt() : 1);
                    foItems.add(m);
                }
            }
            entry.put("fulfillment_order_line_items", foItems);
            byFulfillmentOrder.add(entry);
        }

        if (byFulfillmentOrder.isEmpty()) {
            throw new BadRequestException("No open fulfillment orders for Shopify order " + shopifyOrderId);
        }

        Map<String, Object> trackingInfo = new HashMap<>();
        trackingInfo.put("number", trackingNumber);
        if (trackingUrl != null) trackingInfo.put("url", trackingUrl);
        if (carrierId != null) trackingInfo.put("company", carrierId);

        Map<String, Object> fulfillment = new HashMap<>();
        fulfillment.put("line_items_by_fulfillment_order", byFulfillmentOrder);
        fulfillment.put("tracking_info", trackingInfo);
        fulfillment.put("notify_customer", true);

        log.info("Pushing fulfillment for Shopify order {} with tracking {}", shopifyOrderId, trackingNumber);
        shopifyClient.createFulfillment(shopDomain, accessToken, Map.of("fulfillment", fulfillment));
    }

    private void updateSyncConfig(UUID storeId, String syncType, String status, int processed, int succeeded, int failed, List<String> errors) {
        NxIntegrationSyncConfig config = syncConfigRepository.findByStoreIdAndSyncType(storeId, syncType).orElse(null);
        if (config != null) {
            config.setLastSyncAt(LocalDateTime.now());
            config.setLastSyncStatus(status);
            String __m = processed + " processed, " + succeeded + " OK, " + failed + " failed";

            __m = __m.length() > 250 ? __m.substring(0, 250) + "..." : __m;

            config.setLastSyncMessage(__m);
            syncConfigRepository.save(config);
        }
    }
}
