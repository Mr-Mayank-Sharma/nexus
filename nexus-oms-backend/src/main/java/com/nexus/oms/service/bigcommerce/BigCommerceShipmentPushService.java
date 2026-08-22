package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BigCommerceShipmentPushService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    public BigCommerceShipmentPushService(BigCommerceClient bcClient,
                                           NxBigCommerceConfigRepository configRepository,
                                           NxSyncLogRepository syncLogRepository,
                                           OrderRepository orderRepository,
                                           ShipmentRepository shipmentRepository) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional
    public SyncResult pushShipments(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured. Save your API credentials first."));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("SHIPMENT_PUSH")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();

            List<NxOrder> shippedOrders = orderRepository.findByTenantIdAndStatus(tenantId, "SHIPPED", org.springframework.data.domain.Pageable.unpaged()).getContent();
            for (NxOrder order : shippedOrders) {
                try {
                    if (order.getExternalId() == null) continue;

                    int bcOrderId = Integer.parseInt(order.getExternalId());
                    bcClient.updateOrderStatus(apiPath, config.getAccessToken(), bcOrderId, 2);
                    succeeded++;

                    List<NxShipment> shipments = shipmentRepository.findByOrderId(order.getId());
                    for (NxShipment shipment : shipments) {
                        if (shipment.getTrackingNumber() == null) continue;

                        Integer addressId = resolveShippingAddressId(config, bcOrderId);
                        List<Map<String, Object>> items = resolveOrderItems(config, bcOrderId);
                        if (addressId == null) {
                            failed++;
                            errors.add("Order " + order.getId() + ": no shipping address found in BigCommerce");
                            continue;
                        }

                        Map<String, Object> shipmentData = new HashMap<>();
                        shipmentData.put("order_address_id", addressId);
                        shipmentData.put("tracking_number", shipment.getTrackingNumber());
                        shipmentData.put("shipping_provider", shipment.getCarrierId() != null ? shipment.getCarrierId() : "other");
                        shipmentData.put("items", items);

                        bcClient.createShipment(apiPath, config.getAccessToken(), bcOrderId, shipmentData);
                        succeeded++;
                    }
                    processed++;
                } catch (Exception e) {
                    failed++;
                    errors.add("Order " + order.getId() + ": " + e.getMessage());
                }
            }

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
                .syncType("SHIPMENT_PUSH")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .build();
    }

    private Integer resolveShippingAddressId(NxBigCommerceConfig config, int bcOrderId) {
        try {
            JsonNode addresses = bcClient.getOrderShippingAddresses(
                    config.getApiPath() + "/stores/" + config.getStoreHash(), config.getAccessToken(), bcOrderId);
            if (addresses != null && addresses.isArray() && !addresses.isEmpty()) {
                return addresses.get(0).get("id").asInt();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<Map<String, Object>> resolveOrderItems(NxBigCommerceConfig config, int bcOrderId) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            JsonNode products = bcClient.getOrderProducts(
                    config.getApiPath() + "/stores/" + config.getStoreHash(), config.getAccessToken(), bcOrderId);
            if (products != null && products.isArray()) {
                for (JsonNode product : products) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("order_product_id", product.get("id").asInt());
                    item.put("quantity", product.get("quantity").asInt());
                    items.add(item);
                }
            }
        } catch (Exception ignored) {}
        return items;
    }
}
