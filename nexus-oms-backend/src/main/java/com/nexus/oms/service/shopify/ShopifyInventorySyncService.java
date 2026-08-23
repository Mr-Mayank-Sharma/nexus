package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.IntegrationStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShopifyInventorySyncService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyInventorySyncService.class);

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;

    public ShopifyInventorySyncService(ShopifyClient shopifyClient,
                                        IntegrationStoreService storeService,
                                        ShopifyTokenService tokenService,
                                        NxIntegrationSyncConfigRepository syncConfigRepository,
                                        NxSyncLogRepository syncLogRepository,
                                        NxProductMappingRepository productMappingRepository,
                                        InventoryRepository inventoryRepository) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.productMappingRepository = productMappingRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public SyncResult pushInventory(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = tokenService.getAccessToken(storeId);

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(store.getTenantId())
                .integrationType("SHOPIFY_" + store.getStoreCode())
                .syncType("INVENTORY_PUSH")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;

        try {
            List<NxProductMapping> mappings = productMappingRepository.findAll();
            Map<Long, Long> inventoryItemIds = fetchInventoryItemIds(shopDomain, accessToken);

            for (NxProductMapping mapping : mappings) {
                if (!mapping.getTenantId().equals(store.getTenantId())) continue;
                try {
                    List<NxInventory> invList = inventoryRepository.findByTenantIdAndSku(store.getTenantId(), mapping.getNexusSku());
                    if (invList.isEmpty()) continue;

                    int totalOnHand = invList.stream().mapToInt(NxInventory::getQuantityOnHand).sum();
                    int totalAllocated = invList.stream().mapToInt(NxInventory::getQuantityAllocated).sum();
                    int available = totalOnHand - totalAllocated;

                    Long inventoryItemId = inventoryItemIds.get(mapping.getBcVariantId());
                    if (inventoryItemId != null) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("inventory_item_id", inventoryItemId);
                        data.put("available", Math.max(0, available));
                        data.put("location_id", getLocationId(shopDomain, accessToken, inventoryItemId));
                        shopifyClient.setInventoryLevel(shopDomain, accessToken, data);
                    }
                    succeeded++;
                } catch (Exception e) {
                    log.error("Inventory push failed for mapping sku={} productId={} variantId={} resolvedInvItem={} resolvedLocation={}: {}", mapping.getNexusSku(), mapping.getBcProductId(), mapping.getBcVariantId(), inventoryItemIds.get(mapping.getBcVariantId()), getLocationId(shopDomain, accessToken, inventoryItemIds.get(mapping.getBcVariantId())), e.getMessage());
                    failed++;
                }
                processed++;
            }

            updateSyncConfig(storeId, "INVENTORY_PUSH", "COMPLETED", processed, succeeded, failed, null);
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
                .syncType("INVENTORY_PUSH")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .build();
    }

    private Map<Long, Long> fetchInventoryItemIds(String shopDomain, String accessToken) {
        Map<Long, Long> result = new HashMap<>();
        try {
            Map<String, String> params = new HashMap<>();
            params.put("limit", "250");
            JsonNode response = shopifyClient.getProducts(shopDomain, accessToken, params);
            JsonNode products = response != null ? response.get("products") : null;
            if (products != null) {
                for (JsonNode product : products) {
                    JsonNode variants = product.get("variants");
                    if (variants != null) {
                        for (JsonNode variant : variants) {
                            long variantId = variant.get("id").asLong();
                            long invItemId = variant.has("inventory_item_id") ? variant.get("inventory_item_id").asLong() : 0;
                            if (invItemId > 0) result.put(variantId, invItemId);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private long getLocationId(String shopDomain, String accessToken, Long inventoryItemId) {
        try {
            log.debug("getLocationId: querying inventory_levels for item={}", inventoryItemId);
            JsonNode response = shopifyClient.getInventoryLevels(shopDomain, accessToken,
                    Map.of("inventory_item_ids", String.valueOf(inventoryItemId)));
            JsonNode levels = response != null ? response.get("inventory_levels") : null;
            if (levels != null && levels.isArray() && levels.size() > 0) {
                return levels.get(0).get("location_id").asLong();
            }
            log.warn("getLocationId: no inventory_levels returned for item {}", inventoryItemId);
        } catch (Exception e) {
            log.warn("getLocationId: failed for item {}: {}", inventoryItemId, e.getMessage(), e);
        }
        return 1;
    }

    private void updateSyncConfig(UUID storeId, String syncType, String status, int processed, int succeeded, int failed, List<String> errors) {
        NxIntegrationSyncConfig config = syncConfigRepository.findByStoreIdAndSyncType(storeId, syncType).orElse(null);
        if (config != null) {
            config.setLastSyncAt(LocalDateTime.now());
            config.setLastSyncStatus(status);
            String __m = processed + " processed, " + succeeded + " OK, " + failed + " failed";

            __m = __m.length() > 250 ? __m.substring(0, 250) + "..." : __m;

            config.setLastSyncMessage(__m);
            syncConfigRepository.save(config);
        }
    }
}
