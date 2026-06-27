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
public class ShopifyProductSyncService {

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;
    private final NodeRepository nodeRepository;

    public ShopifyProductSyncService(ShopifyClient shopifyClient,
                                      IntegrationStoreService storeService,
                                      NxIntegrationStoreRepository storeRepository,
                                      NxIntegrationSyncConfigRepository syncConfigRepository,
                                      NxSyncLogRepository syncLogRepository,
                                      NxProductMappingRepository productMappingRepository,
                                      InventoryRepository inventoryRepository,
                                      NodeRepository nodeRepository) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.storeRepository = storeRepository;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.productMappingRepository = productMappingRepository;
        this.inventoryRepository = inventoryRepository;
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    public SyncResult syncProducts(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = storeService.getSetting(storeId, "access_token");

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(store.getTenantId())
                .integrationType("SHOPIFY_" + store.getStoreCode())
                .syncType("PRODUCT_SYNC")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        try {
            Map<String, String> params = new HashMap<>();
            params.put("limit", "250");
            params.put("fields", "id,title,sku,variants");

            JsonNode response = shopifyClient.getProducts(shopDomain, accessToken, params);
            JsonNode products = response != null ? response.get("products") : null;

            List<NxNode> nodes = nodeRepository.findByTenantId(store.getTenantId());

            if (products != null && products.isArray()) {
                for (JsonNode product : products) {
                    try {
                        long productId = product.get("id").asLong();
                        String title = product.has("title") ? product.get("title").asText() : "";

                        JsonNode variants = product.get("variants");
                        if (variants != null && variants.isArray()) {
                            for (JsonNode variant : variants) {
                                long variantId = variant.get("id").asLong();
                                String sku = variant.has("sku") ? variant.get("sku").asText() : ("SPF-" + productId + "-" + variantId);
                                if (sku.isBlank()) sku = "SPF-" + productId + "-" + variantId;

                                NxProductMapping mapping = productMappingRepository
                                        .findByTenantIdAndBcSku(store.getTenantId(), sku)
                                        .orElse(NxProductMapping.builder()
                                                .tenantId(store.getTenantId())
                                                .bcProductId((int) productId)
                                                .bcVariantId((int) variantId)
                                                .bcSku(sku)
                                                .nexusSku(sku)
                                                .nexusProductName(title)
                                                .build());
                                mapping.setLastSyncedAt(LocalDateTime.now());
                                productMappingRepository.save(mapping);

                                if (!nodes.isEmpty()) {
                                    NxNode node = nodes.get(0);
                                    List<NxInventory> invList = inventoryRepository
                                            .findByTenantIdAndSkuAndNodeId(store.getTenantId(), sku, node.getId())
                                            .stream().toList();
                                    if (invList.isEmpty() && !inventoryRepository.findByTenantIdAndSku(store.getTenantId(), sku).isEmpty()) {
                                    } else if (inventoryRepository.findByTenantIdAndSku(store.getTenantId(), sku).isEmpty()) {
                                        inventoryRepository.save(NxInventory.builder()
                                                .tenantId(store.getTenantId())
                                                .sku(sku)
                                                .nodeId(node.getId())
                                                .quantityOnHand(variant.has("inventory_quantity") ? variant.get("inventory_quantity").asInt() : 0)
                                                .quantityAllocated(0).quantityReserved(0).quantityInTransit(0)
                                                .quantityOnOrder(0).quantityDamaged(0).safetyStock(0).reorderPoint(0).reorderQty(0)
                                                .build());
                                    }
                                }
                                succeeded++;
                            }
                        }
                    } catch (Exception e) {
                        failed++;
                    }
                    processed++;
                }
            }

            updateSyncConfig(storeId, "PRODUCT_SYNC", "COMPLETED", processed, succeeded, failed, null);
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
