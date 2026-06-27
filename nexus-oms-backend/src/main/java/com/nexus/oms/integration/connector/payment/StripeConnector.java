package com.nexus.oms.integration.connector.payment;

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
public class StripeConnector extends BaseApiConnector {

    private String webhookSecret;

    public StripeConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                            GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("Stripe")
                .version("1.0.0")
                .vendor("Stripe Inc.")
                .platformType("STRIPE")
                .category("Payments")
                .description("Stripe API connector for payments, refunds, invoices, and subscriptions")
                .website("https://stripe.com/docs/api")
                .docsUrl("https://stripe.com/docs/api")
                .supportedSyncTypes(List.of("PAYMENT_IMPORT", "REFUND_PUSH", "INVOICE_SYNC"))
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("API_KEY"))
                .defaultSettings(Map.of("api_version", "2023-10-16"))
                .requiredSettings(Set.of("secret_key"))
                .maxBatchSize(100)
                .supportsWebhooks(true)
                .supportsRealTimeSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String secretKey = resolveCredential("secret_key");
        if (secretKey != null) {
            defaultHeaders.put("Authorization", "Bearer " + secretKey);
        }
        defaultHeaders.put("Content-Type", "application/x-www-form-urlencoded");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        this.webhookSecret = config.getSetting("webhook_secret");
        config.putSetting("base_url", "https://api.stripe.com");
        super.initialize(config);
    }

    @Override
    public boolean testConnection() {
        JsonNode result = restClient.get(baseUrl, "/v1/balance", defaultHeaders, Map.of());
        return result != null && result.has("available");
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("PAYMENT_IMPORT", () -> {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("limit", "100");
            if (params != null && params.containsKey("created_after")) {
                query.put("created[gte]", String.valueOf(params.get("created_after")));
            }

            JsonNode result = restClient.get(baseUrl, "/v1/charges", defaultHeaders, query);
            int count = result != null && result.has("data") ? result.get("data").size() : 0;
            log.info("Imported {} Stripe charges", count);

            return SyncResult.builder()
                    .syncType("PAYMENT_IMPORT")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(count)
                    .build();
        });
    }

    @Override
    public SyncResult pushRefunds(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("REFUND_PUSH", () -> {
            String chargeId = params != null ? (String) params.get("charge_id") : null;
            if (chargeId == null) {
                return SyncResult.builder().syncType("REFUND_PUSH")
                        .status(SyncResult.Status.FAILED).addError("charge_id required").build();
            }

            Map<String, String> body = new LinkedHashMap<>();
            body.put("charge", chargeId);
            if (params.containsKey("amount")) body.put("amount", String.valueOf(params.get("amount")));

            JsonNode result = restClient.post(baseUrl, "/v1/refunds", defaultHeaders, body);
            boolean success = result != null && result.has("id");
            log.info("Stripe refund {}: {}", success ? "created" : "failed", chargeId);

            return SyncResult.builder()
                    .syncType("REFUND_PUSH")
                    .status(success ? SyncResult.Status.COMPLETED : SyncResult.Status.FAILED)
                    .itemsSucceeded(success ? 1 : 0)
                    .build();
        });
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        String callbackUrl = baseUrl + "/api/v1/integration/webhooks/stripe";
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("url", callbackUrl);
            body.put("enabled_events[]", "charge.completed");
            body.put("enabled_events[]", "charge.refunded");
            body.put("enabled_events[]", "payment_intent.succeeded");
            body.put("enabled_events[]", "payment_intent.payment_failed");
            body.put("enabled_events[]", "invoice.paid");
            body.put("enabled_events[]", "invoice.payment_failed");

            JsonNode result = restClient.post(baseUrl, "/v1/webhook_endpoints", defaultHeaders, body);
            if (result != null && result.has("id")) {
                log.info("Stripe webhook endpoint created: {}", result.get("id").asText());
            }
        } catch (Exception e) {
            log.warn("Failed to register Stripe webhooks: {}", e.getMessage());
        }
    }

    @Override
    public void handleWebhookEvent(com.nexus.oms.integration.dto.IntegrationEvent event) {
        String type = event.getEventType();
        log.info("Stripe webhook: {}", type);
        if ("charge.refunded".equals(type)) {
            String chargeId = event.getPayloadField("id");
            log.info("Processing refund for charge: {}", chargeId);
        }
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "STRIPE"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("Stripe")
                    .vendor("Stripe Inc.")
                    .platformType("STRIPE")
                    .category("Payments")
                    .description("Stripe payment processing")
                    .supportedSyncTypes(List.of("PAYMENT_IMPORT", "REFUND_PUSH", "INVOICE_SYNC"))
                    .supportedAuthTypes(List.of("API_KEY"))
                    .supportsWebhooks(true)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new StripeConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
