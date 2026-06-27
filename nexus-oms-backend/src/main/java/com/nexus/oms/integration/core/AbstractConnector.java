package com.nexus.oms.integration.core;

import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.IntegrationEvent;
import com.nexus.oms.integration.dto.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

public abstract class AbstractConnector implements Connector {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected String id;
    protected ConnectorMetadata metadata;
    protected ConnectorConfig config;
    protected CredentialVault credentialVault;
    protected boolean initialized;

    protected LocalDateTime lastSuccessAt;
    protected LocalDateTime lastErrorAt;
    protected int consecutiveFailures;
    protected long totalSyncs;
    protected long totalErrors;

    @Override
    public String getId() { return id; }

    @Override
    public ConnectorMetadata getMetadata() { return metadata; }

    @Override
    public void initialize(ConnectorConfig config) {
        this.id = config.getId() != null ? config.getId() : UUID.randomUUID().toString();
        this.config = config;
        this.initialized = true;
        log.info("Connector {} initialized", id);
    }

    @Override
    public void shutdown() {
        this.initialized = false;
        log.info("Connector {} shut down", id);
    }

    @Override
    public List<String> getSupportedSyncTypes() {
        return metadata != null ? metadata.getSupportedSyncTypes() : List.of();
    }

    @Override
    public boolean supportsSyncType(String syncType) {
        return getSupportedSyncTypes().contains(syncType);
    }

    @Override
    public SyncResult executeSync(String syncType, UUID tenantId, Map<String, Object> params) {
        if (!supportsSyncType(syncType)) {
            return SyncResult.builder()
                    .syncType(syncType)
                    .status(SyncResult.Status.FAILED)
                    .addError("Unsupported sync type: " + syncType)
                    .build();
        }

        return switch (syncType) {
            case "ORDER_IMPORT" -> syncOrders(tenantId, params);
            case "PRODUCT_SYNC" -> syncProducts(tenantId, params);
            case "INVENTORY_PUSH" -> pushInventory(tenantId, params);
            case "FULFILLMENT_PUSH" -> pushFulfillments(tenantId, params);
            case "REFUND_PUSH" -> pushRefunds(tenantId, params);
            default -> SyncResult.builder()
                    .syncType(syncType)
                    .status(SyncResult.Status.FAILED)
                    .addError("Unknown sync type: " + syncType)
                    .build();
        };
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", id);
        status.put("platform", metadata != null ? metadata.getPlatformType() : "unknown");
        status.put("initialized", initialized);
        status.put("lastSuccessAt", lastSuccessAt);
        status.put("lastErrorAt", lastErrorAt);
        status.put("consecutiveFailures", consecutiveFailures);
        status.put("totalSyncs", totalSyncs);
        status.put("totalErrors", totalErrors);
        return status;
    }

    @Override
    public ConnectorHealth getHealth() {
        ConnectorHealth.Status status = !initialized ? ConnectorHealth.Status.DOWN :
                consecutiveFailures > 5 ? ConnectorHealth.Status.DEGRADED :
                consecutiveFailures > 0 ? ConnectorHealth.Status.DEGRADED :
                ConnectorHealth.Status.UP;

        return ConnectorHealth.builder()
                .connectorId(id)
                .status(status)
                .lastSuccessAt(lastSuccessAt)
                .lastErrorAt(lastErrorAt)
                .consecutiveFailures(consecutiveFailures)
                .build();
    }

    @Override
    public void handleWebhookEvent(IntegrationEvent event) {
        log.info("Webhook event received: type={} source={}", event.getEventType(), event.getSource());
    }

    protected void recordSuccess() {
        lastSuccessAt = LocalDateTime.now();
        consecutiveFailures = 0;
        totalSyncs++;
    }

    protected void recordError() {
        lastErrorAt = LocalDateTime.now();
        consecutiveFailures++;
        totalSyncs++;
        totalErrors++;
    }

    protected void checkInitialized() {
        if (!initialized) throw new IllegalStateException("Connector not initialized");
    }
}
