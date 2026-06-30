package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.shopify.ShopifyWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ShopifyWebhookService shopifyWebhookService;
    private final IntegrationStoreService storeService;

    public WebhookController(ShopifyWebhookService shopifyWebhookService,
                             IntegrationStoreService storeService) {
        this.shopifyWebhookService = shopifyWebhookService;
        this.storeService = storeService;
    }

    @PostMapping("/shopify/orders/create")
    public ResponseEntity<ApiResponse<String>> handleShopifyOrderCreate(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("X-Shopify-Shop-Domain") String shopDomain) {
        log.info("Shopify order create webhook received: orderId={} shop={}", payload.get("id"), shopDomain);

        Optional<NxIntegrationStore> store = storeService.findStoreByExternalDomain(shopDomain);
        if (store.isPresent()) {
            shopifyWebhookService.handleWebhook(payload, store.get().getId());
            return ResponseEntity.ok(ApiResponse.success("Order import triggered", "Webhook processed"));
        }

        log.warn("No store found for Shopify domain: {}", shopDomain);
        return ResponseEntity.ok(ApiResponse.success("Webhook received", "Store not found"));
    }

    @PostMapping("/shopify/orders/fulfilled")
    public ResponseEntity<ApiResponse<String>> handleShopifyOrderFulfilled(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("X-Shopify-Shop-Domain") String shopDomain) {
        log.info("Shopify order fulfilled webhook received: orderId={} shop={}", payload.get("id"), shopDomain);

        Optional<NxIntegrationStore> store = storeService.findStoreByExternalDomain(shopDomain);
        if (store.isPresent()) {
            payload.put("topic", "orders/fulfilled");
            shopifyWebhookService.handleWebhook(payload, store.get().getId());
            return ResponseEntity.ok(ApiResponse.success("Fulfillment processed", "Webhook processed"));
        }

        log.warn("No store found for Shopify domain: {}", shopDomain);
        return ResponseEntity.ok(ApiResponse.success("Webhook received", "Store not found"));
    }
}
