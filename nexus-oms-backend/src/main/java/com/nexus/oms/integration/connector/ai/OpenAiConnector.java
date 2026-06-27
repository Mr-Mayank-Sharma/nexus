package com.nexus.oms.integration.connector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class OpenAiConnector extends BaseApiConnector {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String model;

    public OpenAiConnector(CredentialVault credentialVault, RestProtocolAdapter restClient,
                            GraphqlProtocolAdapter graphqlClient, DataMapper dataMapper) {
        super(credentialVault, restClient, graphqlClient, dataMapper);
        this.metadata = ConnectorMetadata.builder()
                .name("OpenAI")
                .version("1.0.0")
                .vendor("OpenAI")
                .platformType("OPENAI")
                .category("AI Platform")
                .description("OpenAI API connector for LLM inference, embeddings, and AI-powered automation")
                .website("https://platform.openai.com")
                .docsUrl("https://platform.openai.com/docs/api-reference")
                .supportedSyncTypes(List.of("CHAT_COMPLETION", "EMBEDDINGS", "IMAGE_GENERATION", "FINE_TUNING"))
                .supportedProtocols(List.of("REST"))
                .supportedAuthTypes(List.of("API_KEY"))
                .defaultSettings(Map.of("model", "gpt-4o", "api_version", "2024-10"))
                .requiredSettings(Set.of("api_key"))
                .maxBatchSize(50)
                .supportsWebhooks(false)
                .supportsRealTimeSync(true)
                .build();
    }

    @Override
    protected void buildDefaultHeaders() {
        String apiKey = resolveCredential("api_key");
        if (apiKey != null) {
            defaultHeaders.put("Authorization", "Bearer " + apiKey);
        }
        defaultHeaders.put("Content-Type", "application/json");
    }

    @Override
    public void initialize(ConnectorConfig config) {
        this.model = config.getSetting("model");
        if (this.model == null) this.model = "gpt-4o";
        config.putSetting("base_url", "https://api.openai.com");
        super.initialize(config);
    }

    @Override
    public boolean testConnection() {
        JsonNode result = restClient.get(baseUrl, "/v1/models", defaultHeaders, Map.of());
        return result != null && result.has("data");
    }

    public JsonNode chatCompletion(String systemPrompt, String userMessage, Double temperature) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            if (temperature != null) body.put("temperature", temperature);

            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null) {
                messages.addObject().put("role", "system").put("content", systemPrompt);
            }
            messages.addObject().put("role", "user").put("content", userMessage);

            return restClient.post(baseUrl, "/v1/chat/completions", defaultHeaders, body);
        } catch (Exception e) {
            log.error("OpenAI chat completion failed", e);
            return null;
        }
    }

    public JsonNode createEmbedding(String input) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "text-embedding-3-small");
            body.put("input", input);

            return restClient.post(baseUrl, "/v1/embeddings", defaultHeaders, body);
        } catch (Exception e) {
            log.error("OpenAI embedding failed", e);
            return null;
        }
    }

    public String classifyOrderIssue(String description) {
        JsonNode result = chatCompletion(
                "You are an order management assistant. Classify the following issue into one of: " +
                "INVENTORY_SHORTAGE, CARRIER_DELAY, ADDRESS_ERROR, PAYMENT_FAILURE, FRAUD_FLAG, OTHER. " +
                "Respond with only the category name.",
                description, 0.1);
        if (result != null && result.has("choices") && result.get("choices").isArray() &&
            result.get("choices").size() > 0) {
            return result.get("choices").get(0).get("message").get("content").asText().trim();
        }
        return "OTHER";
    }

    public String suggestCarrier(String originZip, String destZip, Double weightKg) {
        JsonNode result = chatCompletion(
                "You are a logistics optimization assistant. Given origin ZIP, destination ZIP, and weight, " +
                "suggest the best carrier (FedEx, UPS, USPS, DHL) and reason in one sentence.",
                String.format("Origin: %s, Destination: %s, Weight: %.1f kg", originZip, destZip, weightKg),
                0.2);
        if (result != null && result.has("choices") && result.get("choices").isArray() &&
            result.get("choices").size() > 0) {
            return result.get("choices").get(0).get("message").get("content").asText().trim();
        }
        return "UPS";
    }

    @Override
    public SyncResult syncOrders(UUID tenantId, Map<String, Object> params) {
        return runWithTiming("AI_PREDICT", () -> {
            log.info("OpenAI prediction requested");
            return SyncResult.builder()
                    .syncType("AI_PREDICT")
                    .status(SyncResult.Status.COMPLETED)
                    .itemsSucceeded(1)
                    .build();
        });
    }

    public static class Factory implements ConnectorFactory {
        @Override public String getPlatformType() { return "OPENAI"; }

        @Override
        public ConnectorMetadata getMetadata() {
            return ConnectorMetadata.builder()
                    .name("OpenAI")
                    .vendor("OpenAI")
                    .platformType("OPENAI")
                    .category("AI Platform")
                    .description("AI LLM and embeddings")
                    .supportedAuthTypes(List.of("API_KEY"))
                    .build();
        }

        @Override
        public com.nexus.oms.integration.core.Connector create(CredentialVault vault) {
            return new OpenAiConnector(vault,
                    new RestProtocolAdapter(), new GraphqlProtocolAdapter(), new DataMapper());
        }
    }
}
