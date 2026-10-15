package com.nexus.oms.integration.connector.communication;

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
public class TwilioConnector extends BaseApiConnector {

    private String accountSid;

    public TwilioConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                            GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("Twilio")
                .version("1.0.0")
                .vendor("Twilio Inc.")
                .platformType("TWILIO")
                .category("Communication")
                .description("Twilio API connector for SMS, WhatsApp, voice, and email notifications")
                .website("https://www.twilio.com/docs")
                .docsUrl("https://www.twilio.com/docs/api")
                .supportedSyncTypes(List.of("SEND_SMS"))
                .previewSyncTypes(List.of())
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("BASIC_AUTH"))
                .requiredSettings(Set.of("account_sid", "auth_token"))
                .maxBatchSize(100)
                .supportsWebhooks(false)
                .supportsRealTimeSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String authToken = resolveCredential("auth_token");
        if (accountSid != null && authToken != null) {
            String auth = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes());
            defaultHeaders.put("Authorization", "Basic " + auth);
        }
        defaultHeaders.put("Content-Type", "application/x-www-form-urlencoded");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        this.accountSid = resolveCredential("account_sid");
        config.putSetting("base_url", "https://api.twilio.com/2010-04-01");
        super.initialize(config);
    }

    @Override
    public boolean testConnection() {
        JsonNode result = restClient.get(baseUrl, "/Accounts/" + accountSid + ".json", defaultHeaders, Map.of());
        return result != null && result.has("sid");
    }

    public SyncResult sendSms(String to, String from, String body) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("To", to);
            params.put("From", from);
            params.put("Body", body);

            JsonNode result = restClient.post(baseUrl,
                    "/Accounts/" + accountSid + "/Messages.json", defaultHeaders, params);
            boolean success = result != null && result.has("sid");
            log.info("Twilio SMS {} to {}", success ? "sent" : "failed", to);

            return SyncResult.builder()
                    .syncType("SEND_SMS")
                    .status(success ? SyncResult.Status.COMPLETED : SyncResult.Status.FAILED)
                    .itemsSucceeded(success ? 1 : 0)
                    .build();
        } catch (Exception e) {
            log.error("Twilio SMS failed", e);
            return SyncResult.builder().syncType("SEND_SMS").status(SyncResult.Status.FAILED)
                    .addError(e.getMessage()).build();
        }
    }

    public SyncResult sendWhatsApp(String to, String from, String body) {
        return sendSms("whatsapp:" + to, "whatsapp:" + from, body);
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        String to = params == null ? null : (String) params.get("to");
        String body = params == null ? null : (String) params.get("body");
        String from = params != null && params.get("from") != null ? (String) params.get("from") : config.getSetting("from_number");
        // No default sender and no default text: +15005550006 is Twilio's magic test number and an invented
        // "your order has shipped" would be sent to a real customer.
        if (to == null || to.isBlank() || body == null || body.isBlank() || from == null || from.isBlank()) {
            return SyncResult.builder().syncType("SEND_SMS").status(SyncResult.Status.FAILED)
                    .addError("to, body and a sender (from, or the from_number setting) are all required").build();
        }
        return sendSms(to, from, body);
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "TWILIO"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("Twilio")
                    .vendor("Twilio Inc.")
                    .platformType("TWILIO")
                    .category("Communication")
                    .description("SMS, WhatsApp, voice notifications")
                    .supportedSyncTypes(List.of("SEND_SMS"))
                .previewSyncTypes(List.of())
                    .supportedAuthTypes(List.of("BASIC_AUTH"))
                    .supportsWebhooks(false)
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new TwilioConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
