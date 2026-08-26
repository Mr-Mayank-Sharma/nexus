package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.IntegrationStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShopifyOrderImportService {

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final ObjectMapper objectMapper;

    public ShopifyOrderImportService(ShopifyClient shopifyClient,
                                      IntegrationStoreService storeService,
                                      ShopifyTokenService tokenService,
                                      NxIntegrationStoreRepository storeRepository,
                                      NxIntegrationSyncConfigRepository syncConfigRepository,
                                      NxSyncLogRepository syncLogRepository,
                                      OrderRepository orderRepository,
                                      OrderItemRepository orderItemRepository,
                                      CustomerRepository customerRepository,
                                      AddressRepository addressRepository,
                                      NxProductMappingRepository productMappingRepository,
                                      ObjectMapper objectMapper) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.storeRepository = storeRepository;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.productMappingRepository = productMappingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyncResult importOrders(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        if (!"SHOPIFY".equalsIgnoreCase(store.getPlatform())) {
            throw new IllegalStateException("Store is not a Shopify store");
        }

        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = tokenService.getAccessToken(storeId);
        if (shopDomain == null || accessToken == null) {
            throw new IllegalStateException("Shopify credentials not configured");
        }

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(store.getTenantId())
                .integrationType("SHOPIFY_" + store.getStoreCode())
                .syncType("ORDER_IMPORT")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            int maxPages = 100; // 250/page x 100 pages covers 25k orders
            String pageInfo = null;

            NxIntegrationSyncConfig syncConfig = syncConfigRepository
                    .findByStoreIdAndSyncType(storeId, "ORDER_IMPORT").orElse(null);

            for (int page = 1; page <= maxPages; page++) {
                Map<String, String> params = new HashMap<>();
                params.put("limit", "250");
                params.put("status", "any");
                if (syncConfig != null && syncConfig.getLastSyncAt() != null) {
                    params.put("updated_at_min", syncConfig.getLastSyncAt()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toString());
                }
                if (pageInfo != null) {
                    // Shopify cursor pagination accepts ONLY limit + page_info
                    params.clear();
                    params.put("limit", "250");
                    params.put("page_info", pageInfo);
                }

                ShopifyClient.ProductPage orderPage = shopifyClient.getOrdersPage(shopDomain, accessToken, params);
                JsonNode response = orderPage != null ? orderPage.body() : null;
                pageInfo = orderPage != null ? orderPage.nextPageInfo() : null;
                JsonNode orders = response != null ? response.get("orders") : null;

                if (orders == null || !orders.isArray() || orders.isEmpty()) {
                    break;
                }

                for (JsonNode shopifyOrder : orders) {
                    try {
                        importSingleOrder(store, shopifyOrder);
                        succeeded++;
                    } catch (Exception e) {
                        failed++;
                        errors.add("Order " + shopifyOrder.get("id").asText() + ": " + e.getMessage());
                    }
                    processed++;
                }

                if (pageInfo == null || pageInfo.isBlank()) {
                    break;
                }
            }

            updateSyncConfig(storeId, "ORDER_IMPORT", "COMPLETED", processed, succeeded, failed, errors);
            syncLog.setStatus("COMPLETED");
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSucceeded(succeeded);
            syncLog.setItemsFailed(failed);
            if (!errors.isEmpty()) syncLog.setErrorMessage(String.join("; ", errors));
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
                .syncType("ORDER_IMPORT")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .message(syncLog.getErrorMessage())
                .build();
    }

    private void importSingleOrder(NxIntegrationStore store, JsonNode shopifyOrder) {
        UUID tenantId = store.getTenantId();
        long shopifyOrderId = shopifyOrder.get("id").asLong();
        String orderNumber = shopifyOrder.has("order_number") ? String.valueOf(shopifyOrder.get("order_number").asInt()) : String.valueOf(shopifyOrderId);
        // Channel-scoped dedup: a Shopify order_number must only match other SHOPIFY rows.
        // The unscoped lookup threw "Query did not return a unique result: 2 results"
        // whenever the numeric id collided with duplicated BigCommerce channel_order_ids.
        Optional<NxOrder> existing = orderRepository.findByTenantIdAndChannelAndChannelOrderId(tenantId, "SHOPIFY", orderNumber);
        if (existing.isEmpty()) {
            existing = orderRepository.findByTenantIdAndChannelOrderId(tenantId, orderNumber)
                    .filter(o -> "SHOPIFY".equalsIgnoreCase(o.getChannel()));
        }
        if (existing.isPresent()) {
            return;
        }
        String status = mapStatus(shopifyOrder.has("financial_status") ? shopifyOrder.get("financial_status").asText() : "pending");

        NxCustomer customer = findOrCreateCustomer(tenantId, shopifyOrder);

        BigDecimal subtotal = money(shopifyOrder.get("subtotal_price"));
        BigDecimal shippingCost = BigDecimal.ZERO;
        JsonNode shippingLines = shopifyOrder.get("shipping_lines");
        if (shippingLines != null && shippingLines.isArray() && shippingLines.size() > 0) {
            shippingCost = money(shippingLines.get(0).get("price"));
        }
        BigDecimal totalTax = money(shopifyOrder.get("total_tax"));
        BigDecimal totalPrice = money(shopifyOrder.get("total_price"));

        JsonNode shipping = shopifyOrder.get("shipping_address");
        String street = "";
        String street2 = null;
        if (shipping != null) {
            street = shipping.has("address1") ? shipping.get("address1").asText() : "";
            street2 = shipping.has("address2") ? shipping.get("address2").asText() : null;
            if (street2 != null && !street2.isBlank()) {
                street = street + " " + street2;
                street2 = null;
            }
        }
        Address shipToAddress = addressRepository.save(Address.builder()
                .tenantId(tenantId)
                .addressLine1(street.isBlank() ? null : street)
                .addressLine2(street2)
                .city(shipping != null && shipping.has("city") ? shipping.get("city").asText() : null)
                .state(shipping != null && shipping.has("province") ? shipping.get("province").asText() : null)
                .postalCode(shipping != null && shipping.has("zip") ? shipping.get("zip").asText() : null)
                .country(shipping != null && shipping.has("country") ? shipping.get("country").asText() : "US")
                .fullName(shipping != null ? (shipping.has("first_name") ? shipping.get("first_name").asText() : "") + " " + (shipping.has("last_name") ? shipping.get("last_name").asText() : "") : null)
                .phone(shipping != null && shipping.has("phone") ? shipping.get("phone").asText() : null)
                .addressType("SHIPPING")
                .build());

        NxOrder order = NxOrder.builder()
                .tenantId(tenantId)
                .externalId(String.valueOf(shopifyOrderId))
                .channel("SHOPIFY")
                .channelOrderId(orderNumber)
                .customerId(customer.getId())
                .status(status)
                .shipToAddress(shipToAddress)
                .currency(shopifyOrder.has("currency") ? shopifyOrder.get("currency").asText() : "USD")
                .subtotal(subtotal)
                .shippingCost(shippingCost)
                .taxAmount(totalTax)
                .total(totalPrice)
                .paymentStatus(shopifyOrder.has("financial_status") ? shopifyOrder.get("financial_status").asText() : null)
                .build();

        // Derive fulfillment type from note_attributes (fulfillment_type / pickup_store),
        // fall back to tags for BOPIS detection
        String fulfillmentType = "STANDARD";
        StringBuilder tags = new StringBuilder();
        JsonNode noteAttrs = shopifyOrder.get("note_attributes");
        if (noteAttrs != null && noteAttrs.isArray()) {
            for (JsonNode attr : noteAttrs) {
                String k = attr.path("name").asText("");
                String v = attr.path("value").asText("");
                if ("fulfillment_type".equals(k) && !v.isBlank()) fulfillmentType = v.toUpperCase();
                if ("pickup_store".equals(k)) {
                    order.setMetadata("{\"pickupStore\":\"" + v + "\"}");
                }
            }
        }
        if (shopifyOrder.has("tags") && !shopifyOrder.get("tags").asText().isBlank()) {
            tags.append(shopifyOrder.get("tags").asText());
            if (fulfillmentType.equals("STANDARD") && tags.toString().toLowerCase().contains("bopis")) {
                fulfillmentType = "BOPIS";
            }
        }
        final String ft = fulfillmentType;
        order.setFulfillmentType(ft);
        order = orderRepository.save(order);

        JsonNode lineItems = shopifyOrder.get("line_items");
        if (lineItems != null && lineItems.isArray()) {
            for (JsonNode item : lineItems) {
                String rawSku = item.has("sku") && !item.get("sku").isNull() ? item.get("sku").asText() : "";
                String sku = (rawSku == null || rawSku.isBlank())
                        ? ("SPF-" + item.get("product_id").asText())
                        : rawSku;
                String productName = item.has("title") ? item.get("title").asText() : sku;
                int qty = item.has("quantity") ? item.get("quantity").asInt() : 1;
                BigDecimal unitPrice = money(item.get("price"));
                BigDecimal totalItemPrice = unitPrice.multiply(BigDecimal.valueOf(qty));
                // Multiple mappings per SKU exist — take the first with an image instead of failing
                String imageUrl = productMappingRepository.findAllByTenantIdAndBcSku(tenantId, sku)
                        .stream().filter(m -> m.getImageUrl() != null).findFirst()
                        .map(NxProductMapping::getImageUrl)
                        .orElseGet(() -> {
                            Long pid = item.has("product_id") ? item.get("product_id").asLong() : null;
                            return pid != null
                                    ? productMappingRepository.findAllByTenantIdAndBcProductId(tenantId, pid)
                                            .stream().filter(m -> m.getImageUrl() != null).findFirst()
                                            .map(NxProductMapping::getImageUrl).orElse(null)
                                    : null;
                        });

                NxOrderItem orderItem = NxOrderItem.builder()
                        .orderId(order.getId())
                        .sku(sku)
                        .productName(productName)
                        .imageUrl(imageUrl)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(totalItemPrice)
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(orderItem);
            }
        }
    }

    private BigDecimal money(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return BigDecimal.ZERO;
        String text = node.asText();
        if (text == null || text.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private NxCustomer findOrCreateCustomer(UUID tenantId, JsonNode shopifyOrder) {
        JsonNode customerNode = shopifyOrder.get("customer");
        String email = customerNode != null && customerNode.has("email") ? customerNode.get("email").asText() : "shopify@unknown.com";
        String name = customerNode != null ?
                (customerNode.has("first_name") ? customerNode.get("first_name").asText() + " " + (customerNode.has("last_name") ? customerNode.get("last_name").asText() : "") : "Shopify Customer")
                : "Shopify Customer";

        // SECURITY: scope the lookup to the importing tenant. The previous
        // findAllByEmail matched customers across tenants (data-leak risk) and
        // could also throw on duplicate rows within a tenant.
        List<NxCustomer> existing = customerRepository.findAllByTenantIdAndEmail(tenantId, email);
        NxCustomer customer;
        if (!existing.isEmpty()) {
            customer = existing.get(0);
        } else {
            customer = customerRepository.save(NxCustomer.builder()
                    .tenantId(tenantId)
                    .name(name)
                    .email(email)
                    .build());
        }
        return customer;
    }

    private String mapStatus(String financialStatus) {
        return switch (financialStatus.toUpperCase()) {
            case "PAID", "PARTIALLY_PAID" -> "PENDING";
            case "REFUNDED", "PARTIALLY_REFUNDED" -> "RETURNED";
            case "VOIDED" -> "CANCELLED";
            default -> "PENDING";
        };
    }

    private void updateSyncConfig(UUID storeId, String syncType, String status, int processed, int succeeded, int failed, List<String> errors) {
        NxIntegrationSyncConfig config = syncConfigRepository.findByStoreIdAndSyncType(storeId, syncType).orElse(null);
        if (config != null) {
            config.setLastSyncAt(LocalDateTime.now());
            config.setLastSyncStatus(status);
            String msg = processed + " processed, " + succeeded + " OK, " + failed + " failed" +
                    (!errors.isEmpty() ? ": " + String.join("; ", errors) : "");
            // last_sync_message is varchar(255) — truncate to avoid rolling back the whole import
            if (msg.length() > 250) msg = msg.substring(0, 250) + "...";
            config.setLastSyncMessage(msg);
            syncConfigRepository.save(config);
        }
    }

}
