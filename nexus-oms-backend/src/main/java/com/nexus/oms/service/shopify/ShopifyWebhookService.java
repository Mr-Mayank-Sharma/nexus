package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.FulfillmentReconciliationService;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.WebhookDedupLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ShopifyWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyWebhookService.class);

    private final ShopifyClient shopifyClient;
    private final IntegrationStoreService storeService;
    private final ShopifyTokenService tokenService;
    private final NxIntegrationStoreRepository storeRepository;
    private final NxShopifyWebhookRepository webhookRepository;
    private final ShopifyOrderImportService orderImportService;
    private final WebhookDedupLedgerService dedupLedger;
    private final FulfillmentReconciliationService fulfillmentReconciliationService;
    private final ObjectMapper objectMapper;

    public ShopifyWebhookService(ShopifyClient shopifyClient,
                                  IntegrationStoreService storeService,
                                  ShopifyTokenService tokenService,
                                  NxIntegrationStoreRepository storeRepository,
                                  NxShopifyWebhookRepository webhookRepository,
                                  ShopifyOrderImportService orderImportService,
                                  WebhookDedupLedgerService dedupLedger,
                                  FulfillmentReconciliationService fulfillmentReconciliationService,
                                  ObjectMapper objectMapper) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.storeRepository = storeRepository;
        this.webhookRepository = webhookRepository;
        this.orderImportService = orderImportService;
        this.dedupLedger = dedupLedger;
        this.fulfillmentReconciliationService = fulfillmentReconciliationService;
        this.objectMapper = objectMapper;
    }

    /** Topics Nexus subscribes to, with the receiver path under /webhooks/shopify. */
    static final Map<String, String> TOPICS = Map.of(
            "orders/create", "/orders/create",
            "orders/updated", "/orders/updated",
            "orders/fulfilled", "/orders/fulfilled",
            "orders/cancelled", "/orders/cancelled",
            "app/uninstalled", "/app/uninstalled");

    /**
     * Subscribes the store to every topic Nexus handles. Safe to repeat: topics Shopify already
     * has for the same address are kept, not duplicated. Returns topic to outcome ("REGISTERED",
     * "EXISTS" or "FAILED: reason"), so a partial failure is visible instead of silently dropped.
     */
    @Transactional
    public Map<String, String> registerWebhooks(UUID storeId, String baseUrl) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = tokenService.getAccessToken(storeId);
        String webhookBase = baseUrl.replaceAll("/+$", "") + "/api/v1/webhooks/shopify";

        Map<String, String> existing = new HashMap<>();   // topic|address -> shopify webhook id
        try {
            JsonNode listed = shopifyClient.getWebhooks(shopDomain, accessToken);
            if (listed != null && listed.has("webhooks")) {
                for (JsonNode hook : listed.get("webhooks")) {
                    existing.put(hook.path("topic").asText() + "|" + hook.path("address").asText(), hook.path("id").asText());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not list existing Shopify webhooks for store {}: {}", storeId, e.getMessage());
        }

        Map<String, String> results = new java.util.TreeMap<>();
        TOPICS.forEach((topic, path) -> {
            String address = webhookBase + path;
            try {
                String known = existing.get(topic + "|" + address);
                if (known != null) {
                    recordWebhook(store, storeId, Long.parseLong(known), topic, address);
                    results.put(topic, "EXISTS");
                    return;
                }
                Map<String, Object> webhook = new HashMap<>();
                webhook.put("topic", topic);
                webhook.put("address", address);
                webhook.put("format", "json");
                JsonNode response = shopifyClient.registerWebhook(shopDomain, accessToken, Map.of("webhook", webhook));
                if (response == null || !response.has("webhook")) {
                    results.put(topic, "FAILED: Shopify returned no webhook");
                    return;
                }
                recordWebhook(store, storeId, response.get("webhook").get("id").asLong(), topic, address);
                results.put(topic, "REGISTERED");
            } catch (RuntimeException e) {
                log.warn("Shopify webhook {} not registered for store {}: {}", topic, storeId, e.getMessage());
                results.put(topic, "FAILED: " + e.getMessage());
            }
        });
        return results;
    }

    private void recordWebhook(NxIntegrationStore store, UUID storeId, long shopifyId, String topic, String address) {
        boolean known = webhookRepository.findByStoreId(storeId).stream()
                .anyMatch(w -> w.getShopifyWebhookId() != null && w.getShopifyWebhookId() == shopifyId);
        if (known) return;
        webhookRepository.save(NxShopifyWebhook.builder()
                .tenantId(store.getTenantId()).storeId(storeId).shopifyWebhookId(shopifyId)
                .topic(topic).address(address).isActive(true).build());
    }

    /**
     * Shopify tells us the app was removed from the shop. The credentials are dead from now on:
     * deactivate the store so no sync keeps failing against a revoked token, and make the state
     * visible so a merchant knows to reconnect.
     */
    @Transactional
    public void handleAppUninstalled(UUID storeId) {
        NxIntegrationStore store = storeService.getStore(storeId);
        store.setIsActive(false);
        store.setStatus("DISCONNECTED");
        storeRepository.save(store);
        tokenService.evict(storeId);
        webhookRepository.findByStoreId(storeId).forEach(w -> {
            w.setIsActive(false);
            webhookRepository.save(w);
        });
        log.warn("Shopify app uninstalled for store {} ({}); store deactivated", store.getStoreCode(), storeId);
    }

    @Transactional
    public void handleWebhook(Map<String, Object> payload, UUID storeId) {
        String topic = (String) payload.get("topic");

        if ("orders/fulfilled".equals(topic)) {
            // Shopify is the system of truth for fulfillment — reconcile directly.
            // The reconciliation service sets its own tenant context from the store.
            NxIntegrationStore store = storeService.getStore(storeId);
            fulfillmentReconciliationService.handleFulfillmentWebhook(store, payload);
            return;
        }

        if (topic != null && topic.startsWith("orders/")) {
            // Deduplication: extract external order ID and check ledger
            String externalOrderId = extractShopifyOrderId(payload);
            if (externalOrderId != null) {
                NxIntegrationStore store = storeService.getStore(storeId);
                if (!dedupLedger.tryClaimProcessing(store.getTenantId(), "SHOPIFY", externalOrderId, null)) {
                    log.info("Skipping duplicate Shopify order webhook: shopify_order_id={}", externalOrderId);
                    return;
                }
            }
            orderImportService.importOrders(storeId);
        }
    }

    private String extractShopifyOrderId(Map<String, Object> payload) {
        try {
            Object id = payload.get("id");
            if (id != null) return String.valueOf(id);
            Object orderId = payload.get("order_id");
            if (orderId != null) return String.valueOf(orderId);
        } catch (Exception ignored) {}
        return null;
    }
}
