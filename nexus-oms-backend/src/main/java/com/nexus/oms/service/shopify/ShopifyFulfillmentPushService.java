package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.IntegrationStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShopifyFulfillmentPushService {

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;

    public ShopifyFulfillmentPushService(ShopifyClient shopifyClient,
                                          IntegrationStoreService storeService,
                                          NxIntegrationStoreRepository storeRepository,
                                          NxIntegrationSyncConfigRepository syncConfigRepository,
                                          NxSyncLogRepository syncLogRepository,
                                          OrderRepository orderRepository,
                                          OrderItemRepository orderItemRepository,
                                          ShipmentRepository shipmentRepository) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.storeRepository = storeRepository;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional
    public SyncResult pushFulfillments(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = storeService.getSetting(storeId, "access_token");

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

                    for (NxShipment shipment : shipments) {
                        if (shipment.getTrackingNumber() == null) continue;

                        List<NxOrderItem> items = orderItemRepository.findByOrderId(order.getId());

                        Map<String, Object> fulfillmentData = new HashMap<>();
                        fulfillmentData.put("tracking_number", shipment.getTrackingNumber());
                        fulfillmentData.put("tracking_url", shipment.getLabelUrl());
                        fulfillmentData.put("notify_customer", true);

                        if (shipment.getCarrierId() != null) {
                            fulfillmentData.put("tracking_company", shipment.getCarrierId());
                        }

                        List<Map<String, Object>> lineItems = new ArrayList<>();
                        for (NxOrderItem item : items) {
                            Map<String, Object> li = new HashMap<>();
                            li.put("quantity", item.getQuantity());
                            lineItems.add(li);
                        }
                        fulfillmentData.put("line_items", lineItems);

                        shopifyClient.createFulfillment(shopDomain, accessToken, shopifyOrderId,
                                Map.of("fulfillment", fulfillmentData));
                        succeeded++;
                    }
                    processed++;
                } catch (Exception e) {
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

    private void updateSyncConfig(UUID storeId, String syncType, String status, int processed, int succeeded, int failed, List<String> errors) {
        NxIntegrationSyncConfig config = syncConfigRepository.findByStoreIdAndSyncType(storeId, syncType).orElse(null);
        if (config != null) {
            config.setLastSyncAt(LocalDateTime.now());
            config.setLastSyncStatus(status);
            config.setLastSyncMessage(processed + " processed, " + succeeded + " OK, " + failed + " failed");
            syncConfigRepository.save(config);
        }
    }
}
