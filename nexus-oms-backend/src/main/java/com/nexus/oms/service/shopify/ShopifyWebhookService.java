package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
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
    private final ObjectMapper objectMapper;

    public ShopifyWebhookService(ShopifyClient shopifyClient,
                                  IntegrationStoreService storeService,
                                  ShopifyTokenService tokenService,
                                  NxIntegrationStoreRepository storeRepository,
                                  NxShopifyWebhookRepository webhookRepository,
                                  ShopifyOrderImportService orderImportService,
                                  WebhookDedupLedgerService dedupLedger,
                                  ObjectMapper objectMapper) {
        this.shopifyClient = shopifyClient;
        this.storeService = storeService;
        this.tokenService = tokenService;
        this.storeRepository = storeRepository;
        this.webhookRepository = webhookRepository;
        this.orderImportService = orderImportService;
        this.dedupLedger = dedupLedger;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registerWebhooks(UUID storeId, String baseUrl) {
        NxIntegrationStore store = storeService.getStore(storeId);
        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String accessToken = tokenService.getAccessToken(storeId);

        String webhookBase = baseUrl + "/api/v1/shopify/webhooks";

        List<Map<String, String>> topics = List.of(
                Map.of("topic", "orders/create", "type", "order_created"),
                Map.of("topic", "orders/updated", "type", "order_updated"),
                Map.of("topic", "orders/fulfilled", "type", "order_fulfilled"),
                Map.of("topic", "products/update", "type", "product_updated"),
                Map.of("topic", "inventory_levels/update", "type", "inventory_updated")
        );

        for (Map<String, String> entry : topics) {
            try {
                Map<String, Object> data = new HashMap<>();
                Map<String, Object> webhook = new HashMap<>();
                webhook.put("topic", entry.get("topic"));
                webhook.put("address", webhookBase + "/" + entry.get("type"));
                webhook.put("format", "json");
                data.put("webhook", webhook);

                JsonNode response = shopifyClient.registerWebhook(shopDomain, accessToken, data);
                if (response != null && response.has("webhook")) {
                    JsonNode hook = response.get("webhook");
                    NxShopifyWebhook wh = NxShopifyWebhook.builder()
                            .tenantId(store.getTenantId())
                            .storeId(storeId)
                            .shopifyWebhookId(hook.get("id").asLong())
                            .topic(entry.get("topic"))
                            .address(webhookBase + "/" + entry.get("type"))
                            .isActive(true)
                            .build();
                    webhookRepository.save(wh);
                }
            } catch (Exception ignored) {}
        }
    }

    @Transactional
    public void handleWebhook(Map<String, Object> payload, UUID storeId) {
        String topic = (String) payload.get("topic");

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
