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
        return notSupported("ORDER_IMPORT");
    }

    @Override
    public SyncResult syncProducts(UUID tenantId, Map<String, Object> params) {
        return notSupported("PRODUCT_SYNC");
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

    /**
     * Automatic webhook registration is only offered where it has been written for the platform's real API.
     * Everywhere else the answer is an explicit refusal that says what to do instead; it used to log a line
     * and report "Webhooks registered".
     */
    @Override
    public void registerWebhooks(String nexusBaseUrl) {
        throw new UnsupportedOperationException(getMetadata().getName()
                + " webhooks are not registered automatically by Nexus; set them up in the platform's own console");
    }

    /**
     * A fetch that proves the platform is reachable and readable but stores nothing in Nexus. It is reported
     * as such, never as an import: the records were counted and dropped.
     */
    protected SyncResult previewOnly(String syncType, int found, String noun) {
        return SyncResult.builder()
                .syncType(syncType)
                .status(SyncResult.Status.NOT_SUPPORTED)
                .itemsSucceeded(0)
                .addWarning(getMetadata().getName() + " returned " + found + " " + noun
                        + ", but this connector only reads them; nothing was imported into Nexus")
                .build();
    }

    /**
     * What an operation this connector never implemented must answer. It used to answer COMPLETED with
     * zero items, so callers (and the marketplace) believed inventory, fulfilments or refunds had synced.
     */
    protected SyncResult notSupported(String syncType) {
        String message = getMetadata().getName() + " does not implement " + syncType + "; nothing was sent";
        log.warn(message);
        return SyncResult.builder()
                .syncType(syncType)
                .status(SyncResult.Status.NOT_SUPPORTED)
                .itemsSucceeded(0)
                .addWarning(message)
                .build();
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
            if (result.getStatus() != SyncResult.Status.NOT_SUPPORTED) recordSuccess();   // a refusal is not a healthy sync
            SyncResult.Builder b = SyncResult.builder()
                    .syncType(syncType)
                    .status(result.getStatus())
                    .itemsSucceeded(result.getItemsSucceeded())
                    .itemsFailed(result.getItemsFailed())
                    .itemsSkipped(result.getItemsSkipped())
                    .startedAt(start)
                    .durationMs(java.time.Duration.between(start, LocalDateTime.now()).toMillis());
            result.getErrors().forEach(b::addError);
            result.getWarnings().forEach(b::addWarning);
            return b.build();
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
