package com.nexus.oms.integration.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.integration.core.AbstractConnector;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.IntegrationEvent;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

public abstract class BaseApiConnector extends AbstractConnector {

    protected final RestProtocolAdapter restClient;
    protected final GraphqlProtocolAdapter graphqlClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected String baseUrl;
    protected String apiVersion;
    protected Map<String, String> defaultHeaders = new LinkedHashMap<>();
    protected DataMapper dataMapper;

    public BaseApiConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                             GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        this.credentialVault = credentialVault;
        this.restClient = restClient;
        this.graphqlClient = graphqlClient;
        this.dataMapper = dataMapper;
    }

    @Override
    public void initialize(ConnectorConfig config) {
        super.initialize(config);
        this.baseUrl = config.getSetting("base_url");
        this.apiVersion = config.getApiVersion() != null ? config.getApiVersion() : "v1";
        buildDefaultHeaders();
    }

    protected abstract void buildDefaultHeaders();

    @Override
    public boolean testConnection() {
        try {
            JsonNode result = restClient.get(baseUrl, "/", defaultHeaders, Map.of());
            return result != null;
        } catch (Exception e) {
            log.warn("Connection test failed for {}: {}", id, e.getMessage());
            return false;
        }
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        log.info("Syncing orders from {} for tenant {}", getMetadata().getName(), tenantId);
        return SyncResult.builder()
                .syncType("ORDER_IMPORT")
                .status(SyncResult.Status.COMPLETED)
                .itemsSucceeded(0)
                .build();
    }

    @Override
    public SyncResult syncProducts(UUID tenantId, Map<String, Object> params) {
        log.info("Syncing products from {} for tenant {}", getMetadata().getName(), tenantId);
        return SyncResult.builder()
                .syncType("PRODUCT_SYNC")
                .status(SyncResult.Status.COMPLETED)
                .itemsSucceeded(0)
                .build();
    }

    @Override
    public SyncResult pushInventory(UUID tenantId, Map<String, Object> params) {
        log.info("Pushing inventory to {} for tenant {}", getMetadata().getName(), tenantId);
        return SyncResult.builder()
                .syncType("INVENTORY_PUSH")
                .status(SyncResult.Status.COMPLETED)
                .itemsSucceeded(0)
                .build();
    }

    @Override
    public SyncResult pushFulfillments(UUID tenantId, Map<String, Object> params) {
        log.info("Pushing fulfillments to {} for tenant {}", getMetadata().getName(), tenantId);
        return SyncResult.builder()
                .syncType("FULFILLMENT_PUSH")
                .status(SyncResult.Status.COMPLETED)
                .itemsSucceeded(0)
                .build();
    }

    @Override
    public SyncResult pushRefunds(UUID tenantId, Map<String, Object> params) {
        log.info("Pushing refunds to {} for tenant {}", getMetadata().getName(), tenantId);
        return SyncResult.builder()
                .syncType("REFUND_PUSH")
                .status(SyncResult.Status.COMPLETED)
                .itemsSucceeded(0)
                .build();
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        log.info("Webhooks not implemented for {}", getMetadata().getName());
    }

    protected String resolveCredential(String key) {
        String cached = credentialVault.getCachedSecret(id + ":" + key);
        if (cached != null) return cached;

        String fromConfig = config.getCredential(key);
        if (fromConfig != null) {
            credentialVault.cacheSecret(id + ":" + key, fromConfig);
            return fromConfig;
        }

        return null;
    }

    protected SyncResult runWithTiming(String syncType, SyncRunner runner) {
        LocalDateTime start = LocalDateTime.now();
        try {
            SyncResult result = runner.run();
            recordSuccess();
            return SyncResult.builder()
                    .syncType(syncType)
                    .status(result.getStatus())
                    .itemsSucceeded(result.getItemsSucceeded())
                    .itemsFailed(result.getItemsFailed())
                    .itemsSkipped(result.getItemsSkipped())
                    .startedAt(start)
                    .durationMs(java.time.Duration.between(start, LocalDateTime.now()).toMillis())
                    .build();
        } catch (Exception e) {
            recordError();
            log.error("Sync failed: {}", syncType, e);
            return SyncResult.builder()
                    .syncType(syncType)
                    .status(SyncResult.Status.FAILED)
                    .addError(e.getMessage())
                    .startedAt(start)
                    .durationMs(java.time.Duration.between(start, LocalDateTime.now()).toMillis())
                    .build();
        }
    }

    @FunctionalInterface
    protected interface SyncRunner {
        SyncResult run() throws Exception;
    }

    protected String buildPath(String... parts) {
        return "/" + String.join("/", parts);
    }
}
