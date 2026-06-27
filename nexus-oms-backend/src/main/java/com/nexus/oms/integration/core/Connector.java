package com.nexus.oms.integration.core;

import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.IntegrationEvent;
import com.nexus.oms.integration.dto.SyncResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Connector {

    String getId();
    ConnectorMetadata getMetadata();
    void initialize(ConnectorConfig config);
    void shutdown();

    boolean testConnection();

    SyncResult syncOrders(UUID tenantId, Map<String, Object> params);
    SyncResult syncProducts(UUID tenantId, Map<String, Object> params);
    SyncResult pushInventory(UUID tenantId, Map<String, Object> params);
    SyncResult pushFulfillments(UUID tenantId, Map<String, Object> params);
    SyncResult pushRefunds(UUID tenantId, Map<String, Object> params);

    List<String> getSupportedSyncTypes();
    boolean supportsSyncType(String syncType);

    SyncResult executeSync(String syncType, UUID tenantId, Map<String, Object> params);

    void registerWebhooks(String baseUrl);
    void handleWebhookEvent(IntegrationEvent event);

    Map<String, Object> getStatus();
    ConnectorHealth getHealth();
}
