package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @PostMapping("/shopify/orders/create")
    public ResponseEntity<ApiResponse<String>> handleShopifyOrderCreate(@RequestBody Map<String, Object> payload) {
        log.info("Shopify order create webhook received: {}", payload.get("id"));
        return ResponseEntity.ok(ApiResponse.success("Webhook received", "Order creation acknowledged"));
    }

    @PostMapping("/shopify/orders/fulfilled")
    public ResponseEntity<ApiResponse<String>> handleShopifyOrderFulfilled(@RequestBody Map<String, Object> payload) {
        log.info("Shopify order fulfilled webhook received: {}", payload.get("id"));
        return ResponseEntity.ok(ApiResponse.success("Webhook received", "Fulfillment acknowledged"));
    }
}
