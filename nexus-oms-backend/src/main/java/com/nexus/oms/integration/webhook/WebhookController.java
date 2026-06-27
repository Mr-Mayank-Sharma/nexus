package com.nexus.oms.integration.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.integration.core.Connector;
import com.nexus.oms.integration.core.ConnectorRegistry;
import com.nexus.oms.integration.core.WebhookManager;
import com.nexus.oms.integration.dto.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/integration/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final ConnectorRegistry registry;
    private final WebhookManager webhookManager;
    private final ObjectMapper objectMapper;

    public WebhookController(ConnectorRegistry registry, WebhookManager webhookManager, ObjectMapper objectMapper) {
        this.registry = registry;
        this.webhookManager = webhookManager;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/shopify")
    public ResponseEntity<String> shopifyWebhook(@RequestHeader Map<String, String> headers,
                                                  @RequestBody String body) {
        String topic = headers.get("x-shopify-topic");
        String shopDomain = headers.get("x-shopify-shop-domain");
        log.info("Shopify webhook: topic={} shop={}", topic, shopDomain);

        try {
            JsonNode payload = objectMapper.readTree(body);
            IntegrationEvent event = IntegrationEvent.builder()
                    .eventType(topic != null ? topic : "shopify.webhook")
                    .source("shopify:" + shopDomain)
                    .tenantId(extractTenantFromShop(shopDomain))
                    .payload(objectMapper.convertValue(payload, Map.class))
                    .build();
            webhookManager.dispatch("shopify/" + topic, event);
        } catch (Exception e) {
            log.error("Failed to process Shopify webhook", e);
        }
        return ResponseEntity.ok("{\"status\":\"received\"}");
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> stripeWebhook(@RequestBody String body) {
        log.info("Stripe webhook received");
        try {
            JsonNode payload = objectMapper.readTree(body);
            String type = payload.has("type") ? payload.get("type").asText() : "unknown";
            IntegrationEvent event = IntegrationEvent.builder()
                    .eventType(type)
                    .source("stripe")
                    .payload(objectMapper.convertValue(payload, Map.class))
                    .build();
            webhookManager.dispatch("stripe/" + type, event);
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
        }
        return ResponseEntity.ok("{\"status\":\"received\"}");
    }

    @PostMapping("/{connectorId}")
    public ResponseEntity<String> genericWebhook(@PathVariable String connectorId,
                                                  @RequestHeader Map<String, String> headers,
                                                  @RequestBody String body) {
        log.info("Webhook for connector: {}", connectorId);
        try {
            Connector connector = registry.getConnector(connectorId);
            JsonNode payload = objectMapper.readTree(body);
            IntegrationEvent event = IntegrationEvent.builder()
                    .eventType(headers.getOrDefault("x-event-type", "webhook"))
                    .source(connectorId)
                    .sourceConnectorId(connectorId)
                    .payload(objectMapper.convertValue(payload, Map.class))
                    .headers(headers)
                    .build();
            connector.handleWebhookEvent(event);
            webhookManager.dispatch(connectorId, event);
        } catch (Exception e) {
            log.error("Webhook processing failed for {}", connectorId, e);
        }
        return ResponseEntity.ok("{\"status\":\"received\"}");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "webhookEndpoints", 3));
    }

    private String extractTenantFromShop(String shopDomain) {
        if (shopDomain == null) return "unknown";
        return shopDomain.replace(".myshopify.com", "").replace(".", "_");
    }
}
