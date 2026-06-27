package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
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
                .orElseThrow(() -> new IllegalStateException("BigCommerce not configured"));

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

                    List<NxShipment> shipments = shipmentRepository.findByOrderId(order.getId());
                    for (NxShipment shipment : shipments) {
                        if (shipment.getTrackingNumber() == null) continue;

                        Map<String, Object> shipmentData = new HashMap<>();
                        shipmentData.put("tracking_number", shipment.getTrackingNumber());
                        shipmentData.put("carrier", shipment.getCarrierId() != null ? shipment.getCarrierId() : "other");
                        shipmentData.put("shipping_provider", shipment.getCarrierId());

                        if (shipment.getOriginNodeId() != null) {
                            shipmentData.put("warehouse_id", shipment.getOriginNodeId().toString());
                        }

                        Map<String, Object> items = new HashMap<>();
                        items.put("quantity", 1);
                        shipmentData.put("items", List.of(items));

                        bcClient.createShipment(apiPath, config.getAccessToken(),
                                Integer.parseInt(order.getExternalId()), shipmentData);
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
}
