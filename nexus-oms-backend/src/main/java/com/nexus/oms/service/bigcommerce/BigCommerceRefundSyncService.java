package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
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
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured. Save your API credentials first."));

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

                    int bcOrderId = Integer.parseInt(order.getExternalId());
                    List<Map<String, Object>> items = resolveOrderItems(config, bcOrderId);
                    if (items.isEmpty()) continue;

                    Map<String, Object> refundData = new HashMap<>();
                    refundData.put("reason", nxReturn.getReason() != null ? nxReturn.getReason() : "Return processed");

                    if (nxReturn.getRefundAmount() != null) {
                        Map<String, Object> total = new HashMap<>();
                        total.put("amount", nxReturn.getRefundAmount().toPlainString());
                        total.put("merchant_amount", nxReturn.getRefundAmount().toPlainString());
                        total.put("currency_code", "USD");
                        total.put("shipping_cost", "0.00");
                        total.put("handling_cost", "0.00");
                        refundData.put("total", total);
                    }

                    Map<String, Object> item = items.get(0);
                    item.put("quantity", item.getOrDefault("quantity", 1));
                    item.put("reason", nxReturn.getReason() != null ? nxReturn.getReason() : "Return processed");
                    refundData.put("items", List.of(item));

                    bcClient.createRefund(apiPath, config.getAccessToken(), bcOrderId, refundData);
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
