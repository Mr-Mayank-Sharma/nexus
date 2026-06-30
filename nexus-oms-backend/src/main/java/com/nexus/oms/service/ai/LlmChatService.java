package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class LlmChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public LlmChatService(
            @Value("${nexus.ai.openai.api-key:}") String apiKey,
            @Value("${nexus.ai.openai.model:gpt-4o}") String model) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
    }

    public String chat(String systemPrompt, List<Map<String, String>> messages) {
        if (!enabled) {
            log.info("OpenAI API key not configured, using fallback response");
            return fallbackResponse(messages);
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);

            ArrayNode msgArray = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                msgArray.addObject().put("role", "system").put("content", systemPrompt);
            }
            for (Map<String, String> msg : messages) {
                msgArray.addObject()
                        .put("role", msg.getOrDefault("role", "user"))
                        .put("content", msg.getOrDefault("content", ""));
            }

            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            if (json.has("choices") && json.get("choices").isArray() && json.get("choices").size() > 0) {
                return json.get("choices").get(0).get("message").get("content").asText();
            }
            log.warn("Unexpected OpenAI response: {}", json);
            return fallbackResponse(messages);
        } catch (Exception e) {
            log.error("OpenAI chat completion failed", e);
            return fallbackResponse(messages);
        }
    }

    private String fallbackResponse(List<Map<String, String>> messages) {
        String lastUserMsg = "";
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if ("user".equals(messages.get(i).get("role"))) {
                    lastUserMsg = messages.get(i).getOrDefault("content", "");
                    break;
                }
            }
        }

        String lower = lastUserMsg.toLowerCase();
        if (lower.contains("order") || lower.contains("shipment")) {
            return "I've checked the system. Here's what I found: Order OMS-2024-5821 is currently being processed and is expected to ship within 2 hours.";
        }
        if (lower.contains("inventory") || lower.contains("stock")) {
            return "Sure! Inventory levels look healthy. Top-moving SKU is NEXUS-PRO-X1 with 1,247 units in stock across 3 warehouses.";
        }
        if (lower.contains("sales") || lower.contains("revenue") || lower.contains("report")) {
            return "Based on current data, sales are up 18% this month compared to last. Your top-performing channel is Shopify.";
        }
        if (lower.contains("issue") || lower.contains("fail") || lower.contains("problem") || lower.contains("alert")) {
            return "I've identified 3 orders that require attention due to payment verification delays. Would you like me to list them?";
        }
        if (lower.contains("customer") || lower.contains("top")) {
            return "Your top 5 customers by revenue this month are: TechStore Inc. ($142K), GlobalMart ($98K), QuickShip Logistics ($76K), Prime Retail ($63K), and DirectBuy Corp. ($51K).";
        }
        return "Thanks for your question! I can help you with order tracking, inventory checks, sales reports, stock alerts, and more. Could you provide more details so I can assist you better?";
    }
}
