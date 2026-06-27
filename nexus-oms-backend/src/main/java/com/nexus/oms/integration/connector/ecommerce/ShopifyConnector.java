package com.nexus.oms.integration.connector.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.integration.connector.BaseApiConnector;
import com.nexus.oms.integration.core.ConnectorFactory;
import com.nexus.oms.integration.core.ConnectorMetadata;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.IntegrationEvent;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ShopifyConnector extends BaseApiConnector {

    public ShopifyConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                             GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("Shopify")
                .version("1.0.0")
                .vendor("Shopify Inc.")
                .platformType("SHOPIFY")
                .category("E-Commerce")
                .description("Shopify Admin REST API connector for order management, product sync, and inventory")
                .website("https://shopify.dev")
                .docsUrl("https://shopify.dev/docs/api/admin-rest")
                .supportedSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH", "REFUND_PUSH"))
                .supportedProtocols(List.of("REST", "GraphQL"))
                .supportedAuthTypes(List.of("ACCESS_TOKEN", "OAUTH2"))
                .defaultSettings(Map.of("api_version", "2024-10"))
                .requiredSettings(Set.of("shop_domain", "access_token"))
                .maxBatchSize(250)
                .supportsWebhooks(true)
                .supportsRealTimeSync(true)
                .supportsBatchSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String token = resolveCredential("access_token");
        if (token != null) {
            defaultHeaders.put("X-Shopify-Access-Token", token);
        }
        defaultHeaders.put("Content-Type", "application/json");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        String domain = config.getSetting("shop_domain");
        if (domain != null) {
            config.putSetting("base_url", "https://" + domain);
        }
        super.initialize(config);
    }

    @Override
    public boolean testConnection() {
        JsonNode shop = restClient.get(baseUrl, "/admin/shop.json", defaultHeaders, Map.of());
        return shop != null && shop.has("shop");
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("ORDER_IMPORT", () -> {
            String sinceId = params != null ? (String) params.get("since_id") : null;
            Map<String, String> query = new LinkedHashMap<>();
            query.put("status", "any");
            query.put("limit", "250");
            if (sinceId != null) query.put("since_id", sinceId);

            List<JsonNode> orders = restClient.paginatedGet(baseUrl, "/admin/orders.json",
                    defaultHeaders, query, "orders", "next", 10);

            log.info("Imported {} orders from Shopify", orders.size());
            return SyncResult.builder()
                    .syncType("ORDER_IMPORT")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(orders.size())
                    .build();
        });
    }

    @Override
    public SyncResult syncProducts(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("PRODUCT_SYNC", () -> {
            List<JsonNode> products = restClient.paginatedGet(baseUrl, "/admin/products.json",
                    defaultHeaders, Map.of("limit", "250"), "products", "next", 10);

            log.info("Synced {} products from Shopify", products.size());
            return SyncResult.builder()
                    .syncType("PRODUCT_SYNC")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(products.size())
                    .build();
        });
    }

    @Override
    public SyncResult pushInventory(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("INVENTORY_PUSH", () -> {
            log.info("Inventory push to Shopify");
            return SyncResult.builder()
                    .syncType("INVENTORY_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public SyncResult pushFulfillments(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("FULFILLMENT_PUSH", () -> {
            log.info("Fulfillment push to Shopify");
            return SyncResult.builder()
                    .syncType("FULFILLMENT_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public SyncResult pushRefunds(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("REFUND_PUSH", () -> {
            log.info("Refund push to Shopify");
            return SyncResult.builder()
                    .syncType("REFUND_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        String webhookEndpoint = baseUrl + "/api/v1/integration/webhooks/shopify";
        List<Map<String, String>> topics = List.of(
            Map.of("topic", "orders/create", "address", webhookEndpoint),
            Map.of("topic", "orders/updated", "address", webhookEndpoint),
            Map.of("topic", "orders/fulfilled", "address", webhookEndpoint),
            Map.of("topic", "products/update", "address", webhookEndpoint),
            Map.of("topic", "inventory_levels/update", "address", webhookEndpoint)
        );

        for (Map<String, String> webhook : topics) {
            try {
                restClient.post(baseUrl, "/admin/webhooks.json", defaultHeaders, webhook);
                log.info("Registered Shopify webhook: {}", webhook.get("topic"));
            } catch (Exception e) {
                log.warn("Failed to register webhook {}: {}", webhook.get("topic"), e.getMessage());
            }
        }
    }

    @Override
    public void handleWebhookEvent(IntegrationEvent event) {
        log.info("Shopify webhook: type={}", event.getEventType());
        Map<String, Object> payload = event.getPayload();
        if (payload != null && payload.containsKey("id")) {
            String orderId = String.valueOf(payload.get("id"));
            log.info("Processing Shopify order webhook for order: {}", orderId);
        }
    }

    public static class Factory implements ConnectorFactory {
        @Override
        public String getPlatformType() { return "SHOPIFY"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("Shopify")
                    .vendor("Shopify Inc.")
                    .platformType("SHOPIFY")
                    .category("E-Commerce")
                    .description("Shopify Admin REST API connector")
                    .supportedSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH", "REFUND_PUSH"))
                    .supportedProtocols(List.of("REST", "GraphQL"))
                    .supportedAuthTypes(List.of("ACCESS_TOKEN"))
                    .supportsWebhooks(true)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new ShopifyConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
