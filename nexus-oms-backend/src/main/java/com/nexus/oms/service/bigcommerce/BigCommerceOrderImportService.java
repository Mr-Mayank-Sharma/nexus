package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BigCommerceOrderImportService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final NodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    public BigCommerceOrderImportService(BigCommerceClient bcClient,
                                          NxBigCommerceConfigRepository configRepository,
                                          NxSyncLogRepository syncLogRepository,
                                          NxProductMappingRepository productMappingRepository,
                                          OrderRepository orderRepository,
                                          OrderItemRepository orderItemRepository,
                                          CustomerRepository customerRepository,
                                          AddressRepository addressRepository,
                                          NodeRepository nodeRepository,
                                          ObjectMapper objectMapper) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.productMappingRepository = productMappingRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.nodeRepository = nodeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyncResult importOrders(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("BigCommerce not configured for this tenant"));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("ORDER_IMPORT")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();
            Map<String, String> params = new HashMap<>();
            params.put("limit", "50");
            params.put("sort", "id:asc");
            if (config.getLastOrderSyncAt() != null) {
                params.put("min_date_modified", config.getLastOrderSyncAt().toString());
            }

            JsonNode orders = bcClient.getOrders(apiPath, config.getAccessToken(), params);

            if (orders != null && orders.isArray()) {
                for (JsonNode bcOrder : orders) {
                    try {
                        importSingleOrder(tenantId, bcOrder, apiPath, config.getAccessToken());
                        succeeded++;
                    } catch (Exception e) {
                        failed++;
                        errors.add("Order " + bcOrder.get("id").asText() + ": " + e.getMessage());
                    }
                    processed++;
                }
            }

            config.setLastOrderSyncAt(LocalDateTime.now());
            configRepository.save(config);

            syncLog.setStatus("COMPLETED");
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSucceeded(succeeded);
            syncLog.setItemsFailed(failed);
            if (!errors.isEmpty()) {
                syncLog.setErrorMessage(String.join("; ", errors));
            }
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

    private void importSingleOrder(UUID tenantId, JsonNode bcOrder, String apiPath, String accessToken) {
        int bcOrderId = bcOrder.get("id").asInt();
        String status = mapStatus(bcOrder.get("status_id").asInt());

        NxCustomer customer = findOrCreateCustomer(tenantId, bcOrder);

        BigDecimal subtotal = new BigDecimal(bcOrder.get("subtotal_ex_tax").decimalValue().toPlainString());
        BigDecimal shippingCost = new BigDecimal(bcOrder.get("shipping_cost_ex_tax").decimalValue().toPlainString());
        BigDecimal taxAmount = new BigDecimal(bcOrder.has("total_tax") ? bcOrder.get("total_tax").decimalValue().toPlainString() : "0");
        BigDecimal total = new BigDecimal(bcOrder.get("total_inc_tax").decimalValue().toPlainString());

        String channel = "BIGCOMMERCE";
        if (bcOrder.has("channel_id") && bcOrder.get("channel_id").asInt() > 1) {
            channel = "BIGCOMMERCE_CHANNEL_" + bcOrder.get("channel_id").asInt();
        }

        JsonNode billing = bcOrder.get("billing_address");
        Address shipToAddress = addressRepository.save(Address.builder()
                .tenantId(tenantId)
                .addressLine1(billing != null && billing.has("street_1") ? billing.get("street_1").asText() : null)
                .addressLine2(billing != null && billing.has("street_2") ? billing.get("street_2").asText() : null)
                .city(billing != null && billing.has("city") ? billing.get("city").asText() : null)
                .state(billing != null && billing.has("state") ? billing.get("state").asText() : null)
                .postalCode(billing != null && billing.has("zip") ? billing.get("zip").asText() : null)
                .country(billing != null && billing.has("country") ? billing.get("country").asText() : "US")
                .fullName(billing != null ? (billing.has("first_name") ? billing.get("first_name").asText() : "") + " " + (billing.has("last_name") ? billing.get("last_name").asText() : "") : null)
                .addressType("SHIPPING")
                .build());

        NxOrder order = NxOrder.builder()
                .tenantId(tenantId)
                .externalId(String.valueOf(bcOrderId))
                .channel(channel)
                .channelOrderId(String.valueOf(bcOrderId))
                .customerId(customer.getId())
                .status(status)
                .shipToAddress(shipToAddress)
                .currency(bcOrder.has("currency_code") ? bcOrder.get("currency_code").asText() : "USD")
                .subtotal(subtotal)
                .shippingCost(shippingCost)
                .taxAmount(taxAmount)
                .total(total)
                .paymentStatus(bcOrder.has("payment_status") ? bcOrder.get("payment_status").asText() : null)
                .build();
        order = orderRepository.save(order);

        JsonNode products = bcOrder.get("products");
        if (products == null || !products.isArray()) {
            products = getOrderProducts(apiPath, accessToken, bcOrderId);
        }

        if (products != null && products.isArray()) {
            for (JsonNode item : products) {
                String sku = item.has("sku") ? item.get("sku").asText() : "UNKNOWN";
                String productName = item.has("name") ? item.get("name").asText() : sku;
                int qty = item.has("quantity") ? item.get("quantity").asInt() : 1;
                BigDecimal unitPrice = new BigDecimal(item.has("price_inc_tax") ? item.get("price_inc_tax").decimalValue().toPlainString() : "0");
                BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(qty));

                NxProductMapping mapping = productMappingRepository.findByTenantIdAndBcSku(tenantId, sku)
                        .orElse(null);
                String nexusSku = mapping != null ? mapping.getNexusSku() : sku;

                NxOrderItem orderItem = NxOrderItem.builder()
                        .orderId(order.getId())
                        .sku(nexusSku)
                        .productName(productName)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(orderItem);
            }
        }
    }

    private JsonNode getOrderProducts(String apiPath, String accessToken, int orderId) {
        try {
            return bcClient.getOrderById(apiPath, accessToken, orderId).get("products");
        } catch (Exception e) {
            return null;
        }
    }

    private NxCustomer findOrCreateCustomer(UUID tenantId, JsonNode bcOrder) {
        JsonNode billing = bcOrder.get("billing_address");
        String email = billing != null && billing.has("email") ? billing.get("email").asText() : "unknown@bigcommerce.com";
        String name = billing != null ? billing.get("first_name").asText() + " " + billing.get("last_name").asText() : "BigCommerce Customer";

        return customerRepository.findByEmail(email)
                .orElseGet(() -> {
                    Address customerAddress = addressRepository.save(Address.builder()
                            .tenantId(tenantId)
                            .addressLine1(billing != null && billing.has("street_1") ? billing.get("street_1").asText() : null)
                            .city(billing != null && billing.has("city") ? billing.get("city").asText() : null)
                            .state(billing != null && billing.has("state") ? billing.get("state").asText() : null)
                            .postalCode(billing != null && billing.has("zip") ? billing.get("zip").asText() : null)
                            .addressType("PRIMARY")
                            .build());
                    return customerRepository.save(NxCustomer.builder()
                            .tenantId(tenantId)
                            .name(name)
                            .email(email)
                            .address(customerAddress)
                            .build());
                });
    }

    private String mapStatus(int bcStatusId) {
        return switch (bcStatusId) {
            case 1 -> "PENDING";
            case 2 -> "SHIPPED";
            case 3 -> "DELIVERED";
            case 4 -> "PENDING";
            case 5 -> "CANCELLED";
            case 6 -> "CANCELLED";
            case 7 -> "PENDING";
            case 8 -> "PENDING";
            case 9 -> "PENDING";
            case 10 -> "PENDING";
            case 11 -> "CANCELLED";
            default -> "PENDING";
        };
    }

}
