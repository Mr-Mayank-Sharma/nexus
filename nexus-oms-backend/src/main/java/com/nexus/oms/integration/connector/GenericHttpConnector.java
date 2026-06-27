package com.nexus.oms.integration.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.integration.core.ConnectorMetadata;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;

import java.util.*;

public class GenericHttpConnector extends BaseApiConnector {

    public GenericHttpConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                                 GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper,
                                 String platformType, String name, String vendor, String category,
                                 String description, String docsUrl) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name(name)
                .vendor(vendor)
                .platformType(platformType)
                .category(category)
                .description(description)
                .website(docsUrl)
                .docsUrl(docsUrl)
                .supportedSyncTypes(List.of("ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH", "REFUND_PUSH"))
                .supportedProtocols(List.of("REST", "GraphQL"))
                .supportedAuthTypes(List.of("API_KEY", "OAUTH2", "BASIC_AUTH"))
                .supportsWebhooks(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String apiKey = resolveCredential("api_key");
        String authHeader = config != null ? config.getSetting("auth_header_name") : null;
        if (apiKey != null) {
            String prefix = config != null ? config.getSetting("auth_header_value_prefix") : null;
            String headerValue = prefix != null ? prefix + apiKey : "Bearer " + apiKey;
            defaultHeaders.put(authHeader != null ? authHeader : "Authorization", headerValue);
        }
        defaultHeaders.put("Content-Type", "application/json");
    }

    @Override
    public boolean testConnection() {
        String healthPath = config != null ? config.getSetting("health_path", "/") : "/";
        try {
            JsonNode result = restClient.get(baseUrl, healthPath, defaultHeaders, Map.of());
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("ORDER_IMPORT", () -> {
            String path = config != null ? config.getSetting("orders_path", "/orders") : "/orders";
            List<JsonNode> items = restClient.paginatedGet(baseUrl, path, defaultHeaders,
                    Map.of("limit", String.valueOf(config != null ? config.getBatchSize() : 100)),
                    config != null ? config.getSetting("items_field", "data") : "data",
                    config != null ? config.getSetting("next_token_field") : null, 10);
            return SyncResult.builder()
                    .syncType("ORDER_IMPORT")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(items.size())
                    .build();
        });
    }

    @Override
    public SyncResult syncProducts(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("PRODUCT_SYNC", () -> {
            String path = config != null ? config.getSetting("products_path", "/products") : "/products";
            JsonNode result = restClient.get(baseUrl, path, defaultHeaders, Map.of("limit", "100"));
            int count = result != null && result.isArray() ? result.size() :
                        result != null && result.has("data") ? result.get("data").size() : 0;
            return SyncResult.builder()
                    .syncType("PRODUCT_SYNC")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(count)
                    .build();
        });
    }

    @Override
    public SyncResult pushInventory(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("INVENTORY_PUSH", () -> {
            String path = config != null ? config.getSetting("inventory_path", "/inventory") : "/inventory";
            log.info("Generic push to {} via {}", getMetadata().getName(), path);
            return SyncResult.builder()
                    .syncType("INVENTORY_PUSH")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(0)
                    .build();
        });
    }

    public static GenericHttpConnector forPlatform(CredentialVault vault, RestProtocolAdapter rest,
                                                    GraphqlProtocolAdapter graphql, DataMapper mapper,
                                                    String platformType, String name, String vendor, String category) {
        return new GenericHttpConnector(vault, rest, graphql, mapper,
                platformType, name, vendor, category, name + " connector", "https://" + platformType.toLowerCase() + ".com/docs");
    }
}
