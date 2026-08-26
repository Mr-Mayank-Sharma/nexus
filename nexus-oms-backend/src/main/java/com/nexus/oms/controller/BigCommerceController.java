package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.BigCommerceConfigRequest;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.NxBigCommerceConfig;
import com.nexus.oms.entity.NxSyncLog;
import com.nexus.oms.repository.NxBigCommerceConfigRepository;
import com.nexus.oms.repository.NxSyncLogRepository;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.scheduler.SyncExecutionLocks;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.bigcommerce.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/integrations/bigcommerce")
public class BigCommerceController {

    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final BigCommerceOrderImportService orderImportService;
    private final BigCommerceProductSyncService productSyncService;
    private final BigCommerceInventorySyncService inventorySyncService;
    private final BigCommerceShipmentPushService shipmentPushService;
    private final BigCommerceRefundSyncService refundSyncService;
    private final BigCommerceCustomerImportService customerImportService;
    private final BigCommerceWebhookService webhookService;
    private final SyncExecutionLocks syncExecutionLocks;

    public BigCommerceController(NxBigCommerceConfigRepository configRepository,
                                  NxSyncLogRepository syncLogRepository,
                                  BigCommerceOrderImportService orderImportService,
                                  BigCommerceProductSyncService productSyncService,
                                  BigCommerceInventorySyncService inventorySyncService,
                                  BigCommerceShipmentPushService shipmentPushService,
                                  BigCommerceRefundSyncService refundSyncService,
                                  BigCommerceCustomerImportService customerImportService,
                                  BigCommerceWebhookService webhookService,
                                  SyncExecutionLocks syncExecutionLocks) {
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.orderImportService = orderImportService;
        this.productSyncService = productSyncService;
        this.inventorySyncService = inventorySyncService;
        this.shipmentPushService = shipmentPushService;
        this.refundSyncService = refundSyncService;
        this.customerImportService = customerImportService;
        this.webhookService = webhookService;
        this.syncExecutionLocks = syncExecutionLocks;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<NxBigCommerceConfig>> getConfig() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(
                configRepository.findByTenantId(tenantId).orElse(null)));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<NxBigCommerceConfig>> updateConfig(
            @Valid @RequestBody BigCommerceConfigRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxBigCommerceConfig config = configRepository.findByTenantId(tenantId).orElse(
                NxBigCommerceConfig.builder().tenantId(tenantId).build()
        );
        config.setStoreHash(request.getStoreHash());
        config.setAccessToken(request.getAccessToken());
        config.setClientId(request.getClientId());
        if (request.getApiPath() != null) config.setApiPath(request.getApiPath());
        if (request.getAutoSyncOrders() != null) config.setAutoSyncOrders(request.getAutoSyncOrders());
        if (request.getAutoSyncInventory() != null) config.setAutoSyncInventory(request.getAutoSyncInventory());
        if (request.getSyncIntervalMinutes() != null) config.setSyncIntervalMinutes(request.getSyncIntervalMinutes());
        config = configRepository.save(config);
        return ResponseEntity.ok(ApiResponse.success(config, "BigCommerce configuration saved"));
    }

    @PostMapping("/sync/orders")
    public ResponseEntity<ApiResponse<SyncResult>> syncOrders() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String lockKey = SyncExecutionLocks.key(tenantId, "ORDER_IMPORT");
        if (!syncExecutionLocks.tryAcquire(lockKey)) {
            throw new BadRequestException("An order import is already running for this tenant");
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    orderImportService.importOrders(tenantId),
                    "Order import completed"));
        } finally {
            syncExecutionLocks.release(lockKey);
        }
    }

    @PostMapping("/sync/products")
    public ResponseEntity<ApiResponse<SyncResult>> syncProducts() {
        return ResponseEntity.ok(ApiResponse.success(
                productSyncService.syncProducts(TenantContext.getCurrentTenantId()),
                "Product sync completed"));
    }

    @PostMapping("/sync/inventory")
    public ResponseEntity<ApiResponse<SyncResult>> pushInventory() {
        return ResponseEntity.ok(ApiResponse.success(
                inventorySyncService.pushInventory(TenantContext.getCurrentTenantId()),
                "Inventory push completed"));
    }

    @PostMapping("/sync/shipments")
    public ResponseEntity<ApiResponse<SyncResult>> pushShipments() {
        return ResponseEntity.ok(ApiResponse.success(
                shipmentPushService.pushShipments(TenantContext.getCurrentTenantId()),
                "Shipment push completed"));
    }

    @PostMapping("/sync/refunds")
    public ResponseEntity<ApiResponse<SyncResult>> pushRefunds() {
        return ResponseEntity.ok(ApiResponse.success(
                refundSyncService.pushRefunds(TenantContext.getCurrentTenantId()),
                "Refund push completed"));
    }

    @PostMapping("/sync/customers")
    public ResponseEntity<ApiResponse<SyncResult>> syncCustomers() {
        return ResponseEntity.ok(ApiResponse.success(
                customerImportService.importCustomers(TenantContext.getCurrentTenantId()),
                "Customer import completed"));
    }

    @GetMapping("/sync-logs")
    public ResponseEntity<ApiResponse<Page<NxSyncLog>>> getSyncLogs(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                syncLogRepository.findByTenantIdAndIntegrationTypeOrderByCreatedAtDesc(
                        TenantContext.getCurrentTenantId(), "BIGCOMMERCE", pageable)));
    }

    @PostMapping("/webhooks/register")
    public ResponseEntity<ApiResponse<Void>> registerWebhooks(@RequestParam String baseUrl) {
        webhookService.registerWebhooks(TenantContext.getCurrentTenantId(), baseUrl);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhooks registered"));
    }

    @PostMapping("/webhooks/{type}")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @PathVariable String type,
            @RequestBody Map<String, Object> payload) {
        webhookService.handleWebhookEvent(payload);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook received"));
    }
}
