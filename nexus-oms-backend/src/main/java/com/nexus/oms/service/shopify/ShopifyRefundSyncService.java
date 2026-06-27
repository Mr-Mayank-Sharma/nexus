package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
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
public class ShopifyRefundSyncService {

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final ReturnRepository returnRepository;
    private final OrderRepository orderRepository;

    public ShopifyRefundSyncService(ShopifyClient shopifyClient,
                                     IntegrationStoreService storeService,
                                     NxIntegrationSyncConfigRepository syncConfigRepository,
                                     NxSyncLogRepository syncLogRepository,
                                     ReturnRepository returnRepository,
                                     OrderRepository orderRepository) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public SyncResult pushRefunds(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = storeService.getSetting(storeId, "access_token");

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(store.getTenantId())
                .integrationType("SHOPIFY_" + store.getStoreCode())
                .syncType("REFUND_PUSH")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;

        try {
            List<NxReturn> completedReturns = returnRepository.findByTenantIdAndStatus(store.getTenantId(), "COMPLETED");
            for (NxReturn nxReturn : completedReturns) {
                try {
                    NxOrder order = orderRepository.findById(nxReturn.getOrderId()).orElse(null);
                    if (order == null || !"SHOPIFY".equals(order.getChannel()) || order.getExternalId() == null) {
                        processed++;
                        continue;
                    }

                    long shopifyOrderId = Long.parseLong(order.getExternalId());

                    Map<String, Object> refundData = new HashMap<>();
                    refundData.put("note", nxReturn.getReason() != null ? nxReturn.getReason() : "Return refund");

                    if (nxReturn.getRefundAmount() != null) {
                        Map<String, Object> transactions = new HashMap<>();
                        transactions.put("amount", nxReturn.getRefundAmount());
                        transactions.put("kind", "refund");
                        transactions.put("gateway", "shopify_payments");
                        refundData.put("transactions", List.of(transactions));
                    }

                    shopifyClient.createRefund(shopDomain, accessToken, shopifyOrderId, refundData);
                    succeeded++;
                    processed++;
                } catch (Exception e) {
                    failed++;
                    processed++;
                }
            }

            updateSyncConfig(storeId, "REFUND_PUSH", "COMPLETED", processed, succeeded, failed, null);
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
                .syncType("REFUND_PUSH")
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
