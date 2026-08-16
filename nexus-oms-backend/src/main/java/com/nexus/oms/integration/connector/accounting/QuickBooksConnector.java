package com.nexus.oms.integration.connector.accounting;

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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuickBooksConnector extends BaseApiConnector {

    private String realmId;
    private String accessToken;
    private final Set<String> pushedRefundKeys = ConcurrentHashMap.newKeySet();

    public QuickBooksConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                                GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("QuickBooks Online")
                .version("1.0.0")
                .vendor("Intuit Inc.")
                .platformType("QUICKBOOKS")
                .category("Accounting")
                .description("QuickBooks Online REST API connector for invoices, payments, customers, and reports")
                .website("https://developer.intuit.com")
                .docsUrl("https://developer.intuit.com/app/developer/qbo/docs/api")
                .supportedSyncTypes(List.of("INVOICE_SYNC", "PAYMENT_SYNC", "CUSTOMER_SYNC", "PAYROLL_SYNC"))
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("OAUTH2"))
                .defaultSettings(Map.of("api_version", "v3", "environment", "production"))
                .requiredSettings(Set.of("client_id", "client_secret", "refresh_token", "realm_id", "company_id"))
                .maxBatchSize(100)
                .supportsWebhooks(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        if (accessToken != null) {
            defaultHeaders.put("Authorization", "Bearer " + accessToken);
        }
        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        config.putSetting("base_url", "https://quickbooks.api.intuit.com");
        super.initialize(config);
        this.realmId = config.getSetting("realm_id");
        String clientId = resolveCredential("client_id");
        String clientSecret = resolveCredential("client_secret");
        String refreshToken = resolveCredential("refresh_token");

        if (clientId != null && clientSecret != null && refreshToken != null) {
            refreshAccessToken(clientId, clientSecret, refreshToken);
        }
    }

    private void refreshAccessToken(String clientId, String clientSecret, String refreshToken) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", refreshToken);

            String auth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Basic " + auth);
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            headers.put("Accept", "application/json");

            JsonNode response = restClient.post(
                    "https://oauth.platform.intuit.com", "/oauth2/v1/tokens/bearer", headers, body);

            if (response != null && response.has("access_token")) {
                this.accessToken = response.get("access_token").asText();
                defaultHeaders.put("Authorization", "Bearer " + accessToken);
                credentialVault.cacheSecret(id + ":access_token", accessToken);
                log.info("QuickBooks OAuth token refreshed");
            }
        } catch (Exception e) {
            log.error("QuickBooks auth refresh failed", e);
        }
    }

    @Override
    public boolean testConnection() {
        JsonNode result = restClient.get(baseUrl,
                "/v3/company/" + realmId + "/companyinfo/" + realmId, defaultHeaders, Map.of());
        return result != null && result.has("CompanyInfo");
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("INVOICE_SYNC", () -> {
            JsonNode result = restClient.get(baseUrl,
                    "/v3/company/" + realmId + "/query?query=select * from Invoice MAXRESULTS 100",
                    defaultHeaders, Map.of());
            int count = result != null && result.has("QueryResponse") &&
                    result.get("QueryResponse").has("Invoice") ?
                    result.get("QueryResponse").get("Invoice").size() : 0;
            log.info("Synced {} invoices from QuickBooks", count);

            return SyncResult.builder()
                    .syncType("INVOICE_SYNC")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(count)
                    .build();
        });
    }

    @Override
    public SyncResult pushRefunds(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("REFUND_PUSH", () -> {
            List<?> refunds = params != null && params.get("refunds") instanceof List<?> l
                    ? l : List.of();
            int succeeded = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();

            for (Object raw : refunds) {
                if (!(raw instanceof Map<?, ?> m)) {
                    skipped++;
                    continue;
                }
                String refundId = String.valueOf(m.get("refundId") == null ? "" : m.get("refundId"));
                String idempotencyKey = refundId.isEmpty()
                        ? UUID.randomUUID().toString() : "nexus-refund-" + refundId;
                if (!pushedRefundKeys.add(idempotencyKey)) {
                    skipped++;
                    continue;
                }

                Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
                headers.put("Idempotency-Key", idempotencyKey);

                Map<String, Object> creditMemo = new LinkedHashMap<>();
                creditMemo.put("IdempotencyKey", idempotencyKey);
                creditMemo.put("TotalAmt", m.get("amount"));
                creditMemo.put("DocNumber", refundId);
                if (m.get("customerRef") != null) {
                    creditMemo.put("CustomerRef", Map.of("value", m.get("customerRef")));
                }
                if (m.get("reason") != null) {
                    creditMemo.put("PrivateNote", m.get("reason"));
                }

                try {
                    JsonNode response = restClient.post(baseUrl,
                            "/v3/company/" + realmId + "/creditmemo", headers, creditMemo);
                    if (response != null && response.has("CreditMemo")) {
                        succeeded++;
                        log.info("QuickBooks credit memo pushed for refund {}", refundId);
                    } else {
                        errors.add("QB returned no CreditMemo for refund " + refundId);
                    }
                } catch (Exception e) {
                    pushedRefundKeys.remove(idempotencyKey);
                    errors.add("QB refund push failed for " + refundId + ": " + e.getMessage());
                    log.error("QuickBooks refund push failed", e);
                }
            }

            SyncResult.Builder result = SyncResult.builder()
                    .syncType("REFUND_PUSH")
                    .status(errors.isEmpty() ? SyncResult.Status.COMPLETED : SyncResult.Status.PARTIAL)
                    .itemsSucceeded(succeeded)
                    .itemsSkipped(skipped)
                    .itemsFailed(errors.size());
            errors.forEach(result::addError);
            return result.build();
        });
    }

    @Override
    public void registerWebhooks(String baseUrl) {
        String callbackUrl = baseUrl + "/api/v1/integration/webhooks/quickbooks";
        try {
            restClient.post("https://quickbooks.api.intuit.com",
                    "/v3/company/" + realmId + "/webhooks", defaultHeaders,
                    Map.of("webhooksUrl", callbackUrl, "events", List.of("Invoice", "Payment", "Customer")));
            log.info("QuickBooks webhook registered: {}", callbackUrl);
        } catch (Exception e) {
            log.warn("Failed to register QuickBooks webhook: {}", e.getMessage());
        }
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "QUICKBOOKS"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("QuickBooks Online")
                    .vendor("Intuit Inc.")
                    .platformType("QUICKBOOKS")
                    .category("Accounting")
                    .description("QuickBooks accounting connector")
                    .supportedSyncTypes(List.of("INVOICE_SYNC", "PAYMENT_SYNC", "CUSTOMER_SYNC"))
                    .supportedAuthTypes(List.of("OAUTH2"))
                    .supportsWebhooks(true)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new QuickBooksConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
