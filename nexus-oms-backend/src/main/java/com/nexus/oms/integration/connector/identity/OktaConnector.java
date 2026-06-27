package com.nexus.oms.integration.connector.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.integration.connector.BaseApiConnector;
import com.nexus.oms.integration.core.ConnectorFactory;
import com.nexus.oms.integration.core.ConnectorMetadata;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.integration.core.DataMapper;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.integration.dto.SyncResult;
import com.nexus.oms.integration.protocol.GraphqlProtocolAdapter;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OktaConnector extends BaseApiConnector {

    public OktaConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                          GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("Okta")
                .version("1.0.0")
                .vendor("Okta Inc.")
                .platformType("OKTA")
                .category("Identity & SSO")
                .description("Okta Identity API connector for user management, SSO, MFA, and group sync")
                .website("https://developer.okta.com")
                .docsUrl("https://developer.okta.com/docs/reference/api")
                .supportedSyncTypes(List.of("USER_SYNC", "GROUP_SYNC", "ROLE_SYNC", "SSO_CONFIG"))
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("API_TOKEN", "OAUTH2"))
                .requiredSettings(Set.of("base_url", "api_token"))
                .maxBatchSize(200)
                .supportsWebhooks(true)
                .supportsRealTimeSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String apiToken = resolveCredential("api_token");
        if (apiToken != null) {
            defaultHeaders.put("Authorization", "SSWS " + apiToken);
        }
        defaultHeaders.put("Content-Type", "application/json");
    }

    @Override
    public boolean testConnection() {
        JsonNode result = restClient.get(baseUrl, "/api/v1/org", defaultHeaders, Map.of());
        return result != null && result.has("id");
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("USER_SYNC", () -> {
            JsonNode result = restClient.get(baseUrl, "/api/v1/users?limit=200", defaultHeaders, Map.of());
            int count = result != null && result.isArray() ? result.size() : 0;
            log.info("Synced {} users from Okta", count);
            return SyncResult.builder()
                    .syncType("USER_SYNC")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(count)
                    .build();
        });
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        log.info("Okta webhooks via Event Hook -> configure in Okta Admin Console");
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "OKTA"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("Okta")
                    .vendor("Okta Inc.")
                    .platformType("OKTA")
                    .category("Identity & SSO")
                    .description("Okta identity provider")
                    .supportedSyncTypes(List.of("USER_SYNC", "GROUP_SYNC"))
                    .supportedAuthTypes(List.of("API_TOKEN"))
                    .supportsWebhooks(true)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new OktaConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
