package com.nexus.oms.scheduler;

import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxIntegrationSyncConfig;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.repository.NxIntegrationSyncConfigRepository;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.bigcommerce.*;
import com.nexus.oms.service.shopify.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SyncSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SyncSchedulerService.class);

    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxIntegrationStoreRepository storeRepository;
    private final IntegrationStoreService storeService;

    private final ShopifyOrderImportService shopifyOrderImportService;
    private final ShopifyProductSyncService shopifyProductSyncService;
    private final ShopifyInventorySyncService shopifyInventorySyncService;
    private final ShopifyFulfillmentPushService shopifyFulfillmentPushService;
    private final ShopifyRefundSyncService shopifyRefundSyncService;

    private final BigCommerceOrderImportService bcOrderImportService;
    private final BigCommerceProductSyncService bcProductSyncService;
    private final BigCommerceInventorySyncService bcInventorySyncService;
    private final BigCommerceShipmentPushService bcShipmentPushService;
    private final BigCommerceRefundSyncService bcRefundSyncService;

    public SyncSchedulerService(NxIntegrationSyncConfigRepository syncConfigRepository,
                                 NxIntegrationStoreRepository storeRepository,
                                 IntegrationStoreService storeService,
                                 ShopifyOrderImportService shopifyOrderImportService,
                                 ShopifyProductSyncService shopifyProductSyncService,
                                 ShopifyInventorySyncService shopifyInventorySyncService,
                                 ShopifyFulfillmentPushService shopifyFulfillmentPushService,
                                 ShopifyRefundSyncService shopifyRefundSyncService,
                                 BigCommerceOrderImportService bcOrderImportService,
                                 BigCommerceProductSyncService bcProductSyncService,
                                 BigCommerceInventorySyncService bcInventorySyncService,
                                 BigCommerceShipmentPushService bcShipmentPushService,
                                 BigCommerceRefundSyncService bcRefundSyncService) {
        this.syncConfigRepository = syncConfigRepository;
        this.storeRepository = storeRepository;
        this.storeService = storeService;
        this.shopifyOrderImportService = shopifyOrderImportService;
        this.shopifyProductSyncService = shopifyProductSyncService;
        this.shopifyInventorySyncService = shopifyInventorySyncService;
        this.shopifyFulfillmentPushService = shopifyFulfillmentPushService;
        this.shopifyRefundSyncService = shopifyRefundSyncService;
        this.bcOrderImportService = bcOrderImportService;
        this.bcProductSyncService = bcProductSyncService;
        this.bcInventorySyncService = bcInventorySyncService;
        this.bcShipmentPushService = bcShipmentPushService;
        this.bcRefundSyncService = bcRefundSyncService;
    }

    @Scheduled(fixedRate = 30000)
    public void processDueSyncs() {
        List<NxIntegrationSyncConfig> allEnabled = syncConfigRepository.findByEnabledTrue();
        LocalDateTime now = LocalDateTime.now();

        for (NxIntegrationSyncConfig syncConfig : allEnabled) {
            if (syncConfig.getIntervalMinutes() == null) continue;

            if (syncConfig.getLastSyncAt() == null ||
                    syncConfig.getLastSyncAt().plusMinutes(syncConfig.getIntervalMinutes()).isBefore(now)) {
                try {
                    executeSync(syncConfig);
                } catch (Exception e) {
                    log.error("Scheduled sync failed: store={} type={} error={}",
                            syncConfig.getStoreId(), syncConfig.getSyncType(), e.getMessage());
                    syncConfig.setLastSyncStatus("FAILED");
                    syncConfig.setLastSyncMessage(e.getMessage());
                    syncConfig.setLastSyncAt(LocalDateTime.now());
                    syncConfigRepository.save(syncConfig);
                }
            }
        }
    }

    @Transactional
    public void executeSync(NxIntegrationSyncConfig syncConfig) {
        NxIntegrationStore store = storeRepository.findById(syncConfig.getStoreId()).orElse(null);
        if (store == null || !store.getIsActive()) return;

        log.info("Running scheduled sync: store={} type={}", store.getStoreCode(), syncConfig.getSyncType());

        syncConfig.setLastSyncAt(LocalDateTime.now());
        syncConfig.setLastSyncStatus("RUNNING");
        syncConfig = syncConfigRepository.save(syncConfig);

        SyncResult result = dispatchSync(store, syncConfig.getSyncType());

        syncConfig.setLastSyncStatus(result != null ? result.getStatus() : "FAILED");
        syncConfig.setLastSyncMessage(result != null ? result.getStatus() : "No result returned");
        syncConfig.setLastSyncAt(LocalDateTime.now());
        syncConfigRepository.save(syncConfig);

        log.info("Scheduled sync complete: store={} type={} status={} succeeded={} failed={}",
                store.getStoreCode(), syncConfig.getSyncType(),
                syncConfig.getLastSyncStatus(),
                result != null ? result.getItemsSucceeded() : 0,
                result != null ? result.getItemsFailed() : 0);
    }

    private SyncResult dispatchSync(NxIntegrationStore store, String syncType) {
        UUID storeId = store.getId();
        UUID tenantId = store.getTenantId();
        String platform = store.getPlatform().toUpperCase();

        if ("SHOPIFY".equals(platform)) {
            return switch (syncType) {
                case "ORDER_IMPORT" -> shopifyOrderImportService.importOrders(storeId);
                case "PRODUCT_SYNC" -> shopifyProductSyncService.syncProducts(storeId);
                case "INVENTORY_PUSH" -> shopifyInventorySyncService.pushInventory(storeId);
                case "FULFILLMENT_PUSH" -> shopifyFulfillmentPushService.pushFulfillments(storeId);
                case "REFUND_PUSH" -> shopifyRefundSyncService.pushRefunds(storeId);
                default -> {
                    log.warn("Unknown sync type {} for Shopify", syncType);
                    yield null;
                }
            };
        } else if ("BIGCOMMERCE".equals(platform)) {
            return switch (syncType) {
                case "ORDER_IMPORT" -> bcOrderImportService.importOrders(tenantId);
                case "PRODUCT_SYNC" -> bcProductSyncService.syncProducts(tenantId);
                case "INVENTORY_PUSH" -> bcInventorySyncService.pushInventory(tenantId);
                case "FULFILLMENT_PUSH" -> bcShipmentPushService.pushShipments(tenantId);
                case "REFUND_PUSH" -> bcRefundSyncService.pushRefunds(tenantId);
                default -> {
                    log.warn("Unknown sync type {} for BigCommerce", syncType);
                    yield null;
                }
            };
        }

        log.warn("Unsupported platform for scheduled sync: {}", platform);
        return null;
    }
}
