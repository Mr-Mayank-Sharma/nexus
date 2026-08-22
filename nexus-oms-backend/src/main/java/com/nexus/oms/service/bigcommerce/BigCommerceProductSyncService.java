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
public class BigCommerceProductSyncService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final NxProductMappingRepository productMappingRepository;
    private final InventoryRepository inventoryRepository;
    private final NodeRepository nodeRepository;

    public BigCommerceProductSyncService(BigCommerceClient bcClient,
                                          NxBigCommerceConfigRepository configRepository,
                                          NxSyncLogRepository syncLogRepository,
                                          NxProductMappingRepository productMappingRepository,
                                          InventoryRepository inventoryRepository,
                                          NodeRepository nodeRepository) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.productMappingRepository = productMappingRepository;
        this.inventoryRepository = inventoryRepository;
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    public SyncResult syncProducts(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured. Save your API credentials first."));

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
            params.put("include", "images");
            params.put("include_fields", "id,name,sku,price,inventory_level");

            JsonNode response = bcClient.getProducts(apiPath, config.getAccessToken(), params);
            JsonNode products = response != null ? response.get("data") : null;

            if (products != null && products.isArray()) {
                for (JsonNode product : products) {
                    try {
                        long bcProductId = product.get("id").asLong();
                        String bcSku = product.has("sku") ? product.get("sku").asText() : "";
                        String name = product.has("name") ? product.get("name").asText() : "";
                        String imageUrl = resolvePrimaryImage(product);

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
                        if (imageUrl != null) mapping.setImageUrl(imageUrl);
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
        return nodeRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String resolvePrimaryImage(JsonNode product) {
        try {
            if (product.has("images") && product.get("images").isArray()) {
                JsonNode images = product.get("images");
                for (JsonNode img : images) {
                    if (img.has("is_thumbnail") && img.get("is_thumbnail").asBoolean()) {
                        return img.get("url_standard").asText();
                    }
                }
                if (!images.isEmpty()) {
                    return images.get(0).get("url_standard").asText();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
