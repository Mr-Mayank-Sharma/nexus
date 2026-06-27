package com.nexus.oms.integration.connector.crm;

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
public class SalesforceConnector extends BaseApiConnector {

    private String instanceUrl;
    private String accessToken;

    public SalesforceConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                                GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("Salesforce")
                .version("1.0.0")
                .vendor("Salesforce Inc.")
                .platformType("SALESFORCE")
                .category("CRM")
                .description("Salesforce REST API connector for orders, accounts, contacts, and opportunities")
                .website("https://developer.salesforce.com")
                .docsUrl("https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest")
                .supportedSyncTypes(List.of("ORDER_IMPORT", "ACCOUNT_SYNC", "CONTACT_SYNC", "OPPORTUNITY_SYNC"))
                .supportedProtocols(List.of("REST", "GraphQL", "SOAP"))
                .supportedAuthTypes(List.of("OAUTH2", "JWT_BEARER"))
                .defaultSettings(Map.of("api_version", "v61.0"))
                .requiredSettings(Set.of("client_id", "client_secret", "username", "password", "security_token"))
                .maxBatchSize(200)
                .supportsWebhooks(true)
                .supportsRealTimeSync(true)
                .supportsBatchSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        if (accessToken != null) {
            defaultHeaders.put("Authorization", "Bearer " + accessToken);
        }
        defaultHeaders.put("Content-Type", "application/json");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        String clientId = resolveCredential("client_id");
        String clientSecret = resolveCredential("client_secret");
        String username = resolveCredential("username");
        String password = resolveCredential("password");
        String securityToken = resolveCredential("security_token");

        if (clientId != null && clientSecret != null && username != null && password != null) {
            authenticate(clientId, clientSecret, username, password + (securityToken != null ? securityToken : ""));
        }

        if (instanceUrl != null) {
            config.putSetting("base_url", instanceUrl);
        }
        super.initialize(config);
    }

    private void authenticate(String clientId, String clientSecret, String username, String password) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("grant_type", "password");
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);
            body.put("username", username);
            body.put("password", password);

            JsonNode response = restClient.post(
                    "https://login.salesforce.com", "/services/oauth2/token",
                    Map.of("Content-Type", "application/x-www-form-urlencoded"), body);

            if (response != null && response.has("access_token") && response.has("instance_url")) {
                this.accessToken = response.get("access_token").asText();
                this.instanceUrl = response.get("instance_url").asText();
                defaultHeaders.put("Authorization", "Bearer " + accessToken);
                credentialVault.cacheSecret(id + ":access_token", accessToken);
                log.info("Salesforce authenticated, instance: {}", instanceUrl);
            }
        } catch (Exception e) {
            log.error("Salesforce auth failed", e);
        }
    }

    @Override
    public boolean testConnection() {
        if (accessToken == null || instanceUrl == null) return false;
        JsonNode result = restClient.get(instanceUrl, "/services/data/" + apiVersion + "/sobjects",
                defaultHeaders, Map.of());
        return result != null && result.has("sobjects");
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("ORDER_IMPORT", () -> {
            String query = "SELECT+Id,OrderNumber,Status,EffectiveDate,TotalAmount,AccountId+FROM+Order";
            JsonNode result = restClient.get(instanceUrl,
                    "/services/data/" + apiVersion + "/query?q=" + query, defaultHeaders, Map.of());
            int count = result != null && result.has("records") ? result.get("records").size() : 0;
            log.info("Imported {} orders from Salesforce", count);

            return SyncResult.builder()
                    .syncType("ORDER_IMPORT")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(count)
                    .build();
        });
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        log.info("Salesforce webhooks via Platform Events or CDC — create in Salesforce Setup");
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "SALESFORCE"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("Salesforce")
                    .vendor("Salesforce Inc.")
                    .platformType("SALESFORCE")
                    .category("CRM")
                    .description("Salesforce CRM connector")
                    .supportedSyncTypes(List.of("ORDER_IMPORT", "ACCOUNT_SYNC", "CONTACT_SYNC"))
                    .supportedAuthTypes(List.of("OAUTH2", "JWT_BEARER"))
                    .supportsWebhooks(true)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new SalesforceConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
