package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.service.shopify.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/shopify")
public class ShopifyController {

    private final ShopifyOrderImportService orderImportService;
    private final ShopifyProductSyncService productSyncService;
    private final ShopifyInventorySyncService inventorySyncService;
    private final ShopifyFulfillmentPushService fulfillmentPushService;
    private final ShopifyRefundSyncService refundSyncService;
    private final ShopifyWebhookService webhookService;

    public ShopifyController(ShopifyOrderImportService orderImportService,
                              ShopifyProductSyncService productSyncService,
                              ShopifyInventorySyncService inventorySyncService,
                              ShopifyFulfillmentPushService fulfillmentPushService,
                              ShopifyRefundSyncService refundSyncService,
                              ShopifyWebhookService webhookService) {
        this.orderImportService = orderImportService;
        this.productSyncService = productSyncService;
        this.inventorySyncService = inventorySyncService;
        this.fulfillmentPushService = fulfillmentPushService;
        this.refundSyncService = refundSyncService;
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getShopifyDashboard() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @PostMapping("/stores/{storeId}/sync/orders")
    public ResponseEntity<ApiResponse<SyncResult>> syncOrders(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                orderImportService.importOrders(storeId), "Shopify orders imported"));
    }

    @PostMapping("/stores/{storeId}/sync/products")
    public ResponseEntity<ApiResponse<SyncResult>> syncProducts(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                productSyncService.syncProducts(storeId), "Shopify products synced"));
    }

    @PostMapping("/stores/{storeId}/sync/inventory")
    public ResponseEntity<ApiResponse<SyncResult>> pushInventory(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                inventorySyncService.pushInventory(storeId), "Shopify inventory pushed"));
    }

    @PostMapping("/stores/{storeId}/sync/fulfillments")
    public ResponseEntity<ApiResponse<SyncResult>> pushFulfillments(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                fulfillmentPushService.pushFulfillments(storeId), "Shopify fulfillments pushed"));
    }

    @PostMapping("/stores/{storeId}/sync/refunds")
    public ResponseEntity<ApiResponse<SyncResult>> pushRefunds(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                refundSyncService.pushRefunds(storeId), "Shopify refunds pushed"));
    }

    @PostMapping("/stores/{storeId}/webhooks/register")
    public ResponseEntity<ApiResponse<Void>> registerWebhooks(
            @PathVariable UUID storeId, @RequestParam String baseUrl) {
        webhookService.registerWebhooks(storeId, baseUrl);
        return ResponseEntity.ok(ApiResponse.success(null, "Shopify webhooks registered"));
    }

    @PostMapping("/webhooks/{type}")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @PathVariable String type, @RequestBody Map<String, Object> payload) {
        webhookService.handleWebhook(payload, null);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook received"));
    }
}
