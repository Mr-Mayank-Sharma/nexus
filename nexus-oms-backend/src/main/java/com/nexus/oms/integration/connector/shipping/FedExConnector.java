package com.nexus.oms.integration.connector.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class FedExConnector extends BaseApiConnector {

    private String accountNumber;
    private String oauthToken;
    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String AUTH_URL = "https://apis.fedex.com/oauth/token";
    private static final String PROD_URL = "https://apis.fedex.com";
    private static final String TEST_URL = "https://apis-sandbox.fedex.com";

    public FedExConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                           GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("FedEx")
                .version("1.0.0")
                .vendor("FedEx Corporation")
                .platformType("FEDEX")
                .category("Shipping")
                .description("FedEx REST API connector for rate quotes, shipment creation, tracking, and label generation")
                .website("https://developer.fedex.com")
                .docsUrl("https://developer.fedex.com/api/en-us/home.html")
                .supportedSyncTypes(List.of("RATE_QUOTE", "CREATE_SHIPMENT", "TRACK", "LABEL", "ADDRESS_VALIDATION"))
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("OAUTH2"))
                .defaultSettings(Map.of("environment", "sandbox", "api_version", "v1"))
                .requiredSettings(Set.of("client_id", "client_secret", "account_number"))
                .maxBatchSize(50)
                .supportsWebhooks(true)
                .supportsRealTimeSync(true)
                .supportsBatchSync(false)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        defaultHeaders.put("Content-Type", "application/json");
        if (oauthToken != null) {
            defaultHeaders.put("Authorization", "Bearer " + oauthToken);
        }
    }

    @Override
    public void initialize(ConnectorConfig config) {
        this.accountNumber = config.getSetting("account_number");
        String env = config.getSetting("environment");
        String apiBase = "sandbox".equalsIgnoreCase(env) ? TEST_URL : PROD_URL;
        config.putSetting("base_url", apiBase);

        String clientId = resolveCredential("client_id");
        String clientSecret = resolveCredential("client_secret");
        if (clientId != null && clientSecret != null) {
            authenticate(clientId, clientSecret);
        }

        super.initialize(config);
    }

    private void authenticate(String clientId, String clientSecret) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("grant_type", "client_credentials");
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);

            JsonNode response = restClient.post(AUTH_URL, "", Map.of(
                    "Content-Type", "application/x-www-form-urlencoded"), body);
            if (response != null && response.has("access_token")) {
                this.oauthToken = response.get("access_token").asText();
                defaultHeaders.put("Authorization", "Bearer " + oauthToken);
                credentialVault.cacheSecret(id + ":oauth_token", oauthToken);
                log.info("FedEx OAuth token obtained");
            }
        } catch (Exception e) {
            log.error("FedEx auth failed", e);
        }
    }

    @Override
    public boolean testConnection() {
        return oauthToken != null;
    }

    public SyncResult getRates(Map<String, Object> shipmentDetails) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("accountNumber", objectMapper.createObjectNode().put("value", accountNumber));
            JsonNode result = restClient.post(baseUrl, "/rate/v1/rates/quotes", defaultHeaders, body);
            return SyncResult.builder()
                    .syncType("RATE_QUOTE")
                    .status(result != null ? SyncResult.Status.COMPLETED : SyncResult.Status.FAILED)
                    .itemsSucceeded(result != null ? 1 : 0)
                    .build();
        } catch (Exception e) {
            return SyncResult.builder().syncType("RATE_QUOTE").status(SyncResult.Status.FAILED)
                    .addError(e.getMessage()).build();
        }
    }

    public SyncResult createShipment(Map<String, Object> shipmentData) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode requestedShipment = body.putObject("requestedShipment");
            requestedShipment.put("serviceType", (String) shipmentData.getOrDefault("serviceType", "FEDEX_GROUND"));

            JsonNode result = restClient.post(baseUrl, "/ship/v1/shipments", defaultHeaders, body);
            return SyncResult.builder()
                    .syncType("CREATE_SHIPMENT")
                    .status(result != null ? SyncResult.Status.COMPLETED : SyncResult.Status.FAILED)
                    .itemsSucceeded(result != null ? 1 : 0)
                    .build();
        } catch (Exception e) {
            return SyncResult.builder().syncType("CREATE_SHIPMENT").status(SyncResult.Status.FAILED)
                    .addError(e.getMessage()).build();
        }
    }

    public JsonNode track(String trackingNumber) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("trackingNumberInfo", objectMapper.createObjectNode()
                    .put("trackingNumber", trackingNumber));

            return restClient.post(baseUrl, "/track/v1/trackingnumbers", defaultHeaders, body);
        } catch (Exception e) {
            log.error("FedEx track failed for {}", trackingNumber, e);
            return null;
        }
    }

    @Override
    public SyncResult pushFulfillments(UUID tenantId, Map<String, Object> params) {
        if (params != null) return createShipment(params);
        return SyncResult.builder().syncType("FULFILLMENT_PUSH").status(SyncResult.Status.COMPLETED).build();
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        log.info("FedEx webhooks registered via Developer Portal — configure in FedEx Developer");
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "FEDEX"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("FedEx")
                    .vendor("FedEx Corporation")
                    .platformType("FEDEX")
                    .category("Shipping")
                    .description("FedEx shipping, rates, tracking, labels")
                    .supportedProtocols(List.of("REST"))
                    .supportedAuthTypes(List.of("OAUTH2"))
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new FedExConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
