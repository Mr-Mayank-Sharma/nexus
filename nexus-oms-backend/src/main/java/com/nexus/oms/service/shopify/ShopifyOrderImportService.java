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
    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final ObjectMapper objectMapper;

    public ShopifyOrderImportService(ShopifyClient shopifyClient,
                                      IntegrationStoreService storeService,
                                      NxIntegrationStoreRepository storeRepository,
                                      NxIntegrationSyncConfigRepository syncConfigRepository,
                                      NxSyncLogRepository syncLogRepository,
                                      OrderRepository orderRepository,
                                      OrderItemRepository orderItemRepository,
                                      CustomerRepository customerRepository,
                                      AddressRepository addressRepository,
                                      ObjectMapper objectMapper) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.storeRepository = storeRepository;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyncResult importOrders(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        if (!"SHOPIFY".equalsIgnoreCase(store.getPlatform())) {
            throw new IllegalStateException("Store is not a Shopify store");
        }

        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = storeService.getSetting(storeId, "access_token");
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
            Map<String, String> params = new HashMap<>();
            params.put("limit", "50");
            params.put("status", "any");

            NxIntegrationSyncConfig syncConfig = syncConfigRepository
                    .findByStoreIdAndSyncType(storeId, "ORDER_IMPORT").orElse(null);
            if (syncConfig != null && syncConfig.getLastSyncAt() != null) {
                params.put("updated_at_min", syncConfig.getLastSyncAt().toString());
            }

            JsonNode response = shopifyClient.getOrders(shopDomain, accessToken, params);
            JsonNode orders = response != null ? response.get("orders") : null;

            if (orders != null && orders.isArray()) {
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
        String status = mapStatus(shopifyOrder.has("financial_status") ? shopifyOrder.get("financial_status").asText() : "pending");

        NxCustomer customer = findOrCreateCustomer(tenantId, shopifyOrder);

        BigDecimal subtotal = new BigDecimal(shopifyOrder.has("subtotal_price") ? shopifyOrder.get("subtotal_price").decimalValue().toPlainString() : "0");
        BigDecimal shippingCost = BigDecimal.ZERO;
        JsonNode shippingLines = shopifyOrder.get("shipping_lines");
        if (shippingLines != null && shippingLines.isArray() && shippingLines.size() > 0) {
            shippingCost = new BigDecimal(shippingLines.get(0).get("price").decimalValue().toPlainString());
        }
        BigDecimal totalTax = new BigDecimal(shopifyOrder.has("total_tax") ? shopifyOrder.get("total_tax").decimalValue().toPlainString() : "0");
        BigDecimal totalPrice = new BigDecimal(shopifyOrder.has("total_price") ? shopifyOrder.get("total_price").decimalValue().toPlainString() : "0");

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
        order = orderRepository.save(order);

        JsonNode lineItems = shopifyOrder.get("line_items");
        if (lineItems != null && lineItems.isArray()) {
            for (JsonNode item : lineItems) {
                String sku = item.has("sku") ? item.get("sku").asText() : ("SPF-" + item.get("product_id").asText());
                String productName = item.has("title") ? item.get("title").asText() : sku;
                int qty = item.has("quantity") ? item.get("quantity").asInt() : 1;
                BigDecimal unitPrice = new BigDecimal(item.has("price") ? item.get("price").decimalValue().toPlainString() : "0");
                BigDecimal totalItemPrice = unitPrice.multiply(BigDecimal.valueOf(qty));

                NxOrderItem orderItem = NxOrderItem.builder()
                        .orderId(order.getId())
                        .sku(sku)
                        .productName(productName)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(totalItemPrice)
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(orderItem);
            }
        }
    }

    private NxCustomer findOrCreateCustomer(UUID tenantId, JsonNode shopifyOrder) {
        JsonNode customerNode = shopifyOrder.get("customer");
        String email = customerNode != null && customerNode.has("email") ? customerNode.get("email").asText() : "shopify@unknown.com";
        String name = customerNode != null ?
                (customerNode.has("first_name") ? customerNode.get("first_name").asText() + " " + (customerNode.has("last_name") ? customerNode.get("last_name").asText() : "") : "Shopify Customer")
                : "Shopify Customer";

        return customerRepository.findByEmail(email)
                .orElseGet(() -> customerRepository.save(NxCustomer.builder()
                        .tenantId(tenantId)
                        .name(name)
                        .email(email)
                        .build()));
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
            config.setLastSyncMessage(processed + " processed, " + succeeded + " OK, " + failed + " failed" +
                    (!errors.isEmpty() ? ": " + String.join("; ", errors) : ""));
            syncConfigRepository.save(config);
        }
    }

}
