package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured for this tenant."));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("ORDER_IMPORT")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        int maxPages = 100;

        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();

            for (int page = 1; page <= maxPages; page++) {
                Map<String, String> params = new HashMap<>();
                params.put("limit", "250");
                params.put("page", String.valueOf(page));
                params.put("sort", "id:asc");
                if (config.getLastOrderSyncAt() != null) {
                    params.put("min_date_modified", toUtcIso(config.getLastOrderSyncAt()));
                }

                JsonNode orders = bcClient.getOrders(apiPath, config.getAccessToken(), params);

                if (orders == null || !orders.isArray() || orders.size() == 0) {
                    break;
                }

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

                if (orders.size() < 250) {
                    break;
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
        String channelOrderId = String.valueOf(bcOrderId);

        if (orderRepository.findByTenantIdAndChannelOrderId(tenantId, channelOrderId).isPresent()) {
            return;
        }

        String status = mapStatus(bcOrder.get("status_id").asInt());

        NxCustomer customer = findOrCreateCustomer(tenantId, bcOrder);

        BigDecimal subtotal = parseDecimal(bcOrder.get("subtotal_ex_tax"));
        BigDecimal shippingCost = parseDecimal(bcOrder.get("shipping_cost_ex_tax"));
        BigDecimal taxAmount = parseDecimal(bcOrder.has("total_tax") ? bcOrder.get("total_tax") : null);
        BigDecimal total = parseDecimal(bcOrder.get("total_inc_tax"));

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
                .externalId(channelOrderId)
                .channel(channel)
                .channelOrderId(channelOrderId)
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

        // Derive fulfillment type from custom_fields (fulfillment_type / pickup_store),
        // fall back to staff_notes: "nexus_ft=<TYPE>;pickup_store=<WH>" or containing "bopis"
        String fulfillmentType = "STANDARD";
        JsonNode customFields = bcOrder.get("custom_fields");
        if (customFields != null && customFields.isArray()) {
            for (JsonNode field : customFields) {
                String name = field.path("name").asText("");
                String value = field.path("value").asText("");
                if ("fulfillment_type".equals(name) && !value.isBlank()) {
                    fulfillmentType = value.toUpperCase();
                }
                if ("pickup_store".equals(name) && !value.isBlank()) {
                    order.setMetadata("{\"pickupStore\":\"" + value + "\"}");
                }
            }
        }
        String staffNotes = bcOrder.has("staff_notes") ? bcOrder.get("staff_notes").asText("") : "";
        for (String part : staffNotes.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && !kv[1].isBlank()) {
                if ("nexus_ft".equals(kv[0].trim())) {
                    fulfillmentType = kv[1].trim().toUpperCase();
                } else if ("pickup_store".equals(kv[0].trim())) {
                    order.setMetadata("{\"pickupStore\":\"" + kv[1].trim() + "\"}");
                }
            }
        }
        if ("STANDARD".equals(fulfillmentType) && staffNotes.toLowerCase().contains("bopis")) {
            fulfillmentType = "BOPIS";
        }
        order.setFulfillmentType(fulfillmentType);

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
                BigDecimal unitPrice = parseDecimal(item.has("price_inc_tax") ? item.get("price_inc_tax") : null);
                BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(qty));

                NxProductMapping mapping = productMappingRepository.findByTenantIdAndBcSku(tenantId, sku)
                        .orElse(null);
                String nexusSku = mapping != null ? mapping.getNexusSku() : sku;
                String imageUrl = mapping != null ? mapping.getImageUrl() : null;

                NxOrderItem orderItem = NxOrderItem.builder()
                        .orderId(order.getId())
                        .sku(nexusSku)
                        .productName(productName)
                        .imageUrl(imageUrl)
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
            return bcClient.getOrderProducts(apiPath, accessToken, orderId);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(node.asText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String toUtcIso(LocalDateTime local) {
        return local.atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private NxCustomer findOrCreateCustomer(UUID tenantId, JsonNode bcOrder) {
        JsonNode billing = bcOrder.get("billing_address");
        String email = billing != null && billing.has("email") ? billing.get("email").asText() : "unknown@bigcommerce.com";
        String name = billing != null ? billing.get("first_name").asText() + " " + billing.get("last_name").asText() : "BigCommerce Customer";

        // SECURITY: tenant-scoped lookup (was findByEmail — matched across tenants)
        List<NxCustomer> existingCustomers = customerRepository.findAllByTenantIdAndEmail(tenantId, email);
        if (!existingCustomers.isEmpty()) {
            return existingCustomers.get(0);
        }
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
    }

    private String mapStatus(int bcStatusId) {
        return switch (bcStatusId) {
            case 1 -> "PENDING";
            case 2 -> "SHIPPED";
            case 3 -> "SHIPPED";
            case 4 -> "DELIVERED";
            case 5, 6 -> "CANCELLED";
            case 7 -> "PENDING";
            case 8 -> "PICKING";
            case 9 -> "PACKING";
            case 10 -> "DELIVERED";
            case 11 -> "CONFIRMED";
            default -> "PENDING";
        };
    }

}
