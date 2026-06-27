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
public class BigCommerceProductSyncService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;

    public BigCommerceProductSyncService(BigCommerceClient bcClient,
                                          NxBigCommerceConfigRepository configRepository,
                                          NxSyncLogRepository syncLogRepository,
                                          NxProductMappingRepository productMappingRepository,
                                          InventoryRepository inventoryRepository) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.productMappingRepository = productMappingRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public SyncResult syncProducts(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("BigCommerce not configured"));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("PRODUCT_SYNC")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();
            Map<String, String> params = new HashMap<>();
            params.put("limit", "250");
            params.put("include_fields", "id,name,sku,price,inventory_level");

            JsonNode response = bcClient.getProducts(apiPath, config.getAccessToken(), params);
            JsonNode products = response != null ? response.get("data") : null;

            if (products != null && products.isArray()) {
                for (JsonNode product : products) {
                    try {
                        int bcProductId = product.get("id").asInt();
                        String bcSku = product.has("sku") ? product.get("sku").asText() : "";
                        String name = product.has("name") ? product.get("name").asText() : "";

                        if (bcSku.isBlank()) continue;

                        NxProductMapping mapping = productMappingRepository
                                .findByTenantIdAndBcSku(tenantId, bcSku)
                                .orElse(null);

                        if (mapping == null) {
                            mapping = NxProductMapping.builder()
                                    .tenantId(tenantId)
                                    .bcProductId(bcProductId)
                                    .bcSku(bcSku)
                                    .nexusSku(bcSku)
                                    .nexusProductName(name)
                                    .build();
                        }
                        mapping.setLastSyncedAt(LocalDateTime.now());
                        productMappingRepository.save(mapping);

                        List<NxInventory> invList = inventoryRepository.findByTenantIdAndSku(tenantId, bcSku);
                        if (invList.isEmpty()) {
                            NxNode anyNode = getAnyNode(tenantId);
                            if (anyNode != null) {
                                NxInventory inv = NxInventory.builder()
                                        .tenantId(tenantId)
                                        .sku(bcSku)
                                        .nodeId(anyNode.getId())
                                        .quantityOnHand(product.has("inventory_level") ? product.get("inventory_level").asInt() : 0)
                                        .quantityAllocated(0)
                                        .quantityReserved(0)
                                        .quantityInTransit(0)
                                        .quantityOnOrder(0)
                                        .quantityDamaged(0)
                                        .safetyStock(0)
                                        .reorderPoint(0)
                                        .reorderQty(0)
                                        .build();
                                inventoryRepository.save(inv);
                            }
                        }

                        succeeded++;
                    } catch (Exception e) {
                        failed++;
                    }
                    processed++;
                }
            }

            config.setLastProductSyncAt(LocalDateTime.now());
            configRepository.save(config);

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
                .syncType("PRODUCT_SYNC")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .build();
    }

    private NxNode getAnyNode(UUID tenantId) {
        return null;
    }
}
