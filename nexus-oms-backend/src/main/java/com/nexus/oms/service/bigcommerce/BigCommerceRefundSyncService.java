package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BigCommerceRefundSyncService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final ReturnRepository returnRepository;
    private final OrderRepository orderRepository;

    public BigCommerceRefundSyncService(BigCommerceClient bcClient,
                                         NxBigCommerceConfigRepository configRepository,
                                         NxSyncLogRepository syncLogRepository,
                                         ReturnRepository returnRepository,
                                         OrderRepository orderRepository) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public SyncResult pushRefunds(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("BigCommerce not configured"));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("REFUND_PUSH")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();

            List<NxReturn> completedReturns = returnRepository.findByTenantIdAndStatus(tenantId, "COMPLETED");
            for (NxReturn nxReturn : completedReturns) {
                try {
                    NxOrder order = orderRepository.findById(nxReturn.getOrderId()).orElse(null);
                    if (order == null || order.getExternalId() == null) continue;

                    Map<String, Object> refundData = new HashMap<>();
                    refundData.put("reason", nxReturn.getReason() != null ? nxReturn.getReason() : "Return processed");

                    if (nxReturn.getRefundAmount() != null) {
                        Map<String, Object> total = new HashMap<>();
                        total.put("amount", nxReturn.getRefundAmount());
                        refundData.put("total", total);
                    }

                    List<Map<String, Object>> items = new ArrayList<>();
                    Map<String, Object> item = new HashMap<>();
                    item.put("quantity", 1);
                    item.put("reason", nxReturn.getReason());
                    items.add(item);
                    refundData.put("items", items);

                    bcClient.createRefund(apiPath, config.getAccessToken(),
                            Integer.parseInt(order.getExternalId()), refundData);
                    succeeded++;
                    processed++;
                } catch (Exception e) {
                    failed++;
                    processed++;
                }
            }

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
}
