package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BigCommerceInventorySyncService {

    private static final Logger log = LoggerFactory.getLogger(BigCommerceInventorySyncService.class);

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
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured. Save your API credentials first."));

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
            List<NxProductMapping> mappings = productMappingRepository.findByTenantId(tenantId);

            for (NxProductMapping mapping : mappings) {
                try {
                    List<NxInventory> invList = inventoryRepository.findByTenantIdAndSku(tenantId, mapping.getNexusSku());
                    if (invList.isEmpty()) continue;

                    int available = sellableToStorefront(invList);

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

    public void pushSkuInventory(UUID tenantId, String sku) {
        if (tenantId == null || sku == null) return;
        try {
            Optional<NxBigCommerceConfig> configOpt = configRepository.findByTenantIdAndIsActiveTrue(tenantId);
            if (configOpt.isEmpty()) return;

            NxBigCommerceConfig config = configOpt.get();
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();

            List<NxProductMapping> mappings = productMappingRepository.findByTenantId(tenantId).stream()
                    .filter(m -> sku.equals(m.getNexusSku()) || sku.equals(m.getBcSku()))
                    .toList();

            for (NxProductMapping mapping : mappings) {
                List<NxInventory> invList = inventoryRepository.findByTenantIdAndSku(tenantId, mapping.getNexusSku());
                if (invList.isEmpty()) continue;

                int available = sellableToStorefront(invList);

                Map<String, Object> inventoryData = new HashMap<>();
                inventoryData.put("inventory_level", available);
                inventoryData.put("inventory_warning_level", 0);

                if (mapping.getBcVariantId() != null && mapping.getBcVariantId() > 0) {
                    inventoryData.put("variant_id", mapping.getBcVariantId());
                }

                bcClient.updateInventory(apiPath, config.getAccessToken(), mapping.getBcProductId(), inventoryData);
                log.info("Real-time inventory push to BigCommerce: sku={} available={}", sku, available);
            }
        } catch (Exception e) {
            log.warn("Real-time inventory push to BigCommerce failed for sku {}: {}", sku, e.getMessage());
        }
    }

    /**
     * Quantity BigCommerce is allowed to offer for sale, aggregated across every holding row.
     *
     * <p>On hand minus already-allocated minus already-reserved minus safety stock. The two reasons
     * this matters beyond the obvious:
     *
     * <ul>
     *   <li>The previous {@code onHand - allocated} ignored {@code quantityReserved} as well as
     *       safety stock, so it over-reported sellable stock and let the storefront oversell units
     *       that Nexus had already reserved for an order.
     *   <li>{@code mapToInt(NxInventory::getQuantityOnHand)} unboxed an {@code Integer} straight into
     *       an {@code int} and threw NPE on a null quantity column. Seeded inventory can carry nulls,
     *       so this is a real failure mode, not a theoretical one.
     * </ul>
     *
     * <p>Clamped at zero: BigCommerce rejects a negative {@code inventory_level}, and a negative sum
     * is the correct answer when reservations and safety stock exceed on-hand.
     */
    static int sellableToStorefront(List<NxInventory> rows) {
        int onHand = 0;
        int allocated = 0;
        int reserved = 0;
        int safetyStock = 0;
        for (NxInventory row : rows) {
            onHand += nz(row.getQuantityOnHand());
            allocated += nz(row.getQuantityAllocated());
            reserved += nz(row.getQuantityReserved());
            safetyStock += nz(row.getSafetyStock());
        }
        return Math.max(0, onHand - allocated - reserved - safetyStock);
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }
}
