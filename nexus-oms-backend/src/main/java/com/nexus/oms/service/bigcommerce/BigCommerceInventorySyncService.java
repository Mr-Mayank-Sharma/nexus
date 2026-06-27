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
public class BigCommerceInventorySyncService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;

    public BigCommerceInventorySyncService(BigCommerceClient bcClient,
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
    public SyncResult pushInventory(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("BigCommerce not configured"));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("INVENTORY_PUSH")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();
            List<NxProductMapping> mappings = productMappingRepository.findAll();

            for (NxProductMapping mapping : mappings) {
                try {
                    List<NxInventory> invList = inventoryRepository.findByTenantIdAndSku(tenantId, mapping.getNexusSku());
                    if (invList.isEmpty()) continue;

                    int totalOnHand = invList.stream().mapToInt(NxInventory::getQuantityOnHand).sum();
                    int totalAllocated = invList.stream().mapToInt(NxInventory::getQuantityAllocated).sum();
                    int available = totalOnHand - totalAllocated;

                    Map<String, Object> inventoryData = new HashMap<>();
                    inventoryData.put("inventory_level", available);
                    inventoryData.put("inventory_warning_level", 0);

                    if (mapping.getBcVariantId() != null && mapping.getBcVariantId() > 0) {
                        inventoryData.put("variant_id", mapping.getBcVariantId());
                    }

                    bcClient.updateInventory(apiPath, config.getAccessToken(), mapping.getBcProductId(), inventoryData);
                    succeeded++;
                } catch (Exception e) {
                    failed++;
                    errors.add("Product " + mapping.getBcProductId() + ": " + e.getMessage());
                }
                processed++;
            }

            config.setLastInventorySyncAt(LocalDateTime.now());
            configRepository.save(config);

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
                .syncType("INVENTORY_PUSH")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .message(syncLog.getErrorMessage())
                .build();
    }
}
