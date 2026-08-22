package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.WebhookDedupLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BigCommerceWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BigCommerceWebhookService.class);

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxBigCommerceWebhookRepository webhookRepository;
    private final BigCommerceOrderImportService orderImportService;
    private final BigCommerceProductSyncService productSyncService;
    private final BigCommerceShipmentPushService shipmentPushService;
    private final WebhookDedupLedgerService dedupLedger;
    private final ObjectMapper objectMapper;

    public BigCommerceWebhookService(BigCommerceClient bcClient,
                                      NxBigCommerceConfigRepository configRepository,
                                      NxBigCommerceWebhookRepository webhookRepository,
                                      BigCommerceOrderImportService orderImportService,
                                      BigCommerceProductSyncService productSyncService,
                                      BigCommerceShipmentPushService shipmentPushService,
                                      WebhookDedupLedgerService dedupLedger,
                                      ObjectMapper objectMapper) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.webhookRepository = webhookRepository;
        this.orderImportService = orderImportService;
        this.productSyncService = productSyncService;
        this.shipmentPushService = shipmentPushService;
        this.dedupLedger = dedupLedger;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registerWebhooks(UUID tenantId, String baseUrl) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured. Save your API credentials first."));

        String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();
        String webhookBase = baseUrl + "/api/v1/integrations/bigcommerce/webhooks";

        List<Map<String, String>> scopes = List.of(
                Map.of("scope", "store/order/created", "type", "ORDER_CREATED"),
                Map.of("scope", "store/order/updated", "type", "ORDER_UPDATED"),
                Map.of("scope", "store/product/updated", "type", "PRODUCT_UPDATED"),
                Map.of("scope", "store/shipment/created", "type", "SHIPMENT_CREATED")
        );

        for (Map<String, String> entry : scopes) {
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
        }
    }

    @Transactional
    public void handleWebhookEvent(Map<String, Object> payload) {
        String scope = (String) payload.get("scope");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        if (data == null) return;

        UUID tenantId = extractTenantFromPayload(payload);
        if (tenantId == null) {
            log.warn("Ignoring BigCommerce webhook: no tenant found for store_hash={}", payload.get("store_hash"));
            return;
        }

        if (scope == null) return;

        if (scope.contains("order")) {
            String externalOrderId = extractBigCommerceOrderId(data);
            if (externalOrderId != null) {
                if (!dedupLedger.tryClaimProcessing(tenantId, "BIGCOMMERCE", externalOrderId, null)) {
                    log.info("Skipping duplicate BigCommerce order webhook: bc_order_id={}", externalOrderId);
                    return;
                }
            }
            orderImportService.importOrders(tenantId);
        } else if (scope.contains("product")) {
            productSyncService.syncProducts(tenantId);
        } else if (scope.contains("shipment")) {
            shipmentPushService.pushShipments(tenantId);
        }
    }

    private String extractBigCommerceOrderId(Map<String, Object> data) {
        try {
            Object id = data.get("id");
            if (id != null) return String.valueOf(id);
            Object orderId = data.get("order_id");
            if (orderId != null) return String.valueOf(orderId);
        } catch (Exception ignored) {}
        return null;
    }

    private UUID extractTenantFromPayload(Map<String, Object> payload) {
        Object storeHash = payload.get("store_hash");
        if (storeHash == null) return null;
        return configRepository.findByStoreHash(String.valueOf(storeHash))
                .map(NxBigCommerceConfig::getTenantId)
                .orElse(null);
    }
}
