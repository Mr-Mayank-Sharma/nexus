package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BigCommerceWebhookService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxBigCommerceWebhookRepository webhookRepository;
    private final BigCommerceOrderImportService orderImportService;
    private final ObjectMapper objectMapper;

    public BigCommerceWebhookService(BigCommerceClient bcClient,
                                      NxBigCommerceConfigRepository configRepository,
                                      NxBigCommerceWebhookRepository webhookRepository,
                                      BigCommerceOrderImportService orderImportService,
                                      ObjectMapper objectMapper) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.webhookRepository = webhookRepository;
        this.orderImportService = orderImportService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registerWebhooks(UUID tenantId, String baseUrl) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new IllegalStateException("BigCommerce not configured"));

        String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();
        String webhookBase = baseUrl + "/api/v1/bigcommerce/webhooks";

        List<Map<String, String>> scopes = List.of(
                Map.of("scope", "store/order/created", "type", "ORDER_CREATED"),
                Map.of("scope", "store/order/updated", "type", "ORDER_UPDATED"),
                Map.of("scope", "store/product/updated", "type", "PRODUCT_UPDATED"),
                Map.of("scope", "store/shipment/created", "type", "SHIPMENT_CREATED")
        );

        for (Map<String, String> entry : scopes) {
            try {
                JsonNode response = bcClient.registerWebhook(apiPath, config.getAccessToken(),
                        entry.get("scope"), webhookBase + "/" + entry.get("type").toLowerCase());

                if (response != null && response.has("data")) {
                    JsonNode data = response.get("data");
                    NxBigCommerceWebhook webhook = NxBigCommerceWebhook.builder()
                            .tenantId(tenantId)
                            .webhookId(data.get("id").asInt())
                            .scope(entry.get("scope"))
                            .destination(webhookBase + "/" + entry.get("type").toLowerCase())
                            .isActive(true)
                            .build();
                    webhookRepository.save(webhook);
                }
            } catch (Exception ignored) {}
        }
    }

    @Transactional
    public void handleWebhookEvent(Map<String, Object> payload) {
        String scope = (String) payload.get("scope");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        if (data == null) return;

        UUID tenantId = extractTenantFromPayload(payload);
        if (tenantId == null) return;

        if (scope != null && scope.contains("order")) {
            orderImportService.importOrders(tenantId);
        }
    }

    private UUID extractTenantFromPayload(Map<String, Object> payload) {
        return null;
    }
}
