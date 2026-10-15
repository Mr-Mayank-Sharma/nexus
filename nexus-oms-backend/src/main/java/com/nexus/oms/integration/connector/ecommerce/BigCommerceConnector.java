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
public class BigCommerceConnector extends BaseApiConnector {

    public static final String DEFAULT_API_PATH = "https://api.bigcommerce.com";

    public BigCommerceConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                                 GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("BigCommerce")
                .version("1.0.0")
                .vendor("BigCommerce Pty. Ltd.")
                .platformType("BIGCOMMERCE")
                .category("E-Commerce")
                .description("BigCommerce REST API connector for order management, product sync, and inventory")
                .website("https://developer.bigcommerce.com")
                .docsUrl("https://developer.bigcommerce.com/docs/rest")
                .supportedSyncTypes(List.of())
                .previewSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC"))
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("ACCESS_TOKEN", "OAUTH2"))
                .defaultSettings(Map.of("api_path", DEFAULT_API_PATH))
                .requiredSettings(Set.of("store_hash", "access_token"))
                .maxBatchSize(250)
                .supportsWebhooks(false)
                .supportsRealTimeSync(true)
                .supportsBatchSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String token = resolveCredential("access_token");
        if (token != null) {
            defaultHeaders.put("X-Auth-Token", token);
        }
        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        String apiPath = config.getSetting("api_path");
        if (apiPath == null || apiPath.isBlank()) {
            apiPath = DEFAULT_API_PATH;
            config.putSetting("api_path", apiPath);
        }
        config.putSetting("base_url", apiPath);
        super.initialize(config);
    }

    @Override
    public boolean testConnection() {
        JsonNode store = restClient.get(baseUrl, "/v2/store", defaultHeaders, Map.of());
        return store != null && store.has("store");
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("ORDER_IMPORT", () -> {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("limit", "250");
            if (params != null && params.get("status_id") != null) {
                query.put("status_id", String.valueOf(params.get("status_id")));
            }

            List<JsonNode> orders = restClient.paginatedGet(baseUrl, "/v2/orders",
                    defaultHeaders, query, "orders", "next", 10);

            log.info("Fetched {} orders from BigCommerce", orders.size());
            return previewOnly("ORDER_IMPORT", orders.size(), "orders");
        });
    }

    @Override
    public SyncResult syncProducts(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("PRODUCT_SYNC", () -> {
            List<JsonNode> products = restClient.paginatedGet(baseUrl, "/v3/catalog/products",
                    defaultHeaders, Map.of("limit", "250"), "data", "next", 10);

            log.info("Fetched {} products from BigCommerce", products.size());
            return previewOnly("PRODUCT_SYNC", products.size(), "products");
        });
    }

    @Override
    public SyncResult pushInventory(UUID tenantId, Map<String, Object> params) {
        return notSupported("INVENTORY_PUSH");
    }

    @Override
    public SyncResult pushFulfillments(UUID tenantId, Map<String, Object> params) {
        return notSupported("FULFILLMENT_PUSH");
    }

    @Override
    public SyncResult pushRefunds(UUID tenantId, Map<String, Object> params) {
        return notSupported("REFUND_PUSH");
    }


    @Override
    public void handleWebhookEvent(IntegrationEvent event) {
        log.info("BigCommerce webhook: type={}", event.getEventType());
        Map<String, Object> payload = event.getPayload();
        if (payload != null && payload.containsKey("data")) {
            Object data = payload.get("data");
            if (data instanceof Map) {
                Object orderId = ((Map<?, ?>) data).get("id");
                if (orderId != null) {
                    log.info("Processing BigCommerce webhook for entity id: {}", orderId);
                }
            }
        }
    }

    public static class Factory implements ConnectorFactory {
        @Override
        public String getPlatformType() { return "BIGCOMMERCE"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("BigCommerce")
                    .vendor("BigCommerce Pty. Ltd.")
                    .platformType("BIGCOMMERCE")
                    .category("E-Commerce")
                    .description("BigCommerce REST API connector")
                    .supportedSyncTypes(List.of())
                .previewSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC"))
                    .supportedProtocols(List.of("REST"))
                    .supportedAuthTypes(List.of("ACCESS_TOKEN"))
                    .supportsWebhooks(false)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new BigCommerceConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}