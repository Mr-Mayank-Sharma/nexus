package com.nexus.oms.service.ai;

import com.nexus.oms.exception.AiServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nexus.oms.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core LLM chat service with GPT-4o integration.
 * Features: JSON mode, semantic caching, circuit breaker, cost tracking.
 */
@Service
public class LlmChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SemanticCacheService cacheService;
    private final AiCostTrackingService costTrackingService;
    private final MeterRegistry meterRegistry;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public LlmChatService(
            @Value("${nexus.ai.openai.api-key:}") String apiKey,
            @Value("${nexus.ai.openai.model:gpt-4o}") String model,
            @Value("${nexus.ai.timeout-ms:60000}") int timeoutMs,
            SemanticCacheService cacheService,
            AiCostTrackingService costTrackingService,
            MeterRegistry meterRegistry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.cacheService = cacheService;
        this.costTrackingService = costTrackingService;
        this.meterRegistry = meterRegistry;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
    }

    /**
     * Standard chat completion with caching and cost tracking.
     */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "chatFallback")
    public String chat(String systemPrompt, List<Map<String, String>> messages) {
        if (!enabled) {
            throw unavailable("no model is configured (set nexus.ai.openai.api-key)");
        }

        // Check cache first
        String fullPrompt = (systemPrompt != null ? systemPrompt : "") + ":" + messages;
        Optional<String> cached = cacheService.get(fullPrompt, model);
        if (cached.isPresent()) {
            meterRegistry.counter("nexus.ai.llm.cache_hit").increment();
            return cached.get();
        }

        try {
            long startTime = System.currentTimeMillis();
            ObjectNode body = buildChatBody(systemPrompt, messages, false, null);

            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    String.class);

            long latencyMs = System.currentTimeMillis() - startTime;

            JsonNode json = objectMapper.readTree(response.getBody());
            if (json.has("choices") && json.get("choices").isArray() && json.get("choices").size() > 0) {
                String content = json.get("choices").get(0).get("message").get("content").asText();

                // Extract token usage
                int inputTokens = 0;
                int outputTokens = 0;
                if (json.has("usage")) {
                    inputTokens = json.get("usage").get("prompt_tokens").asInt();
                    outputTokens = json.get("usage").get("completion_tokens").asInt();
                }

                // Track cost
                costTrackingService.recordCost(model, "CHAT", inputTokens, outputTokens,
                        UUID.randomUUID().toString(), "LlmChatService.chat", (int) latencyMs, false, null);

                // Cache the response
                UUID tenantId = null;
                try { tenantId = TenantContext.getCurrentTenantId(); } catch (IllegalStateException ignored) {}
                cacheService.put(fullPrompt, model, content, tenantId);

                meterRegistry.counter("nexus.ai.llm.success").increment();
                return content;
            }

            log.warn("Unexpected OpenAI response: {}", json);
            meterRegistry.counter("nexus.ai.llm.empty_response").increment();
            throw unavailable("the model returned no usable answer");

        } catch (Exception e) {
            log.error("OpenAI chat completion failed", e);
            meterRegistry.counter("nexus.ai.llm.error").increment();
            throw unavailable("the model returned no usable answer");
        }
    }

    /**
     * Chat with JSON response format. Parses the result into a JsonNode.
     */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "chatJsonFallback")
    public JsonNode chatJson(String systemPrompt, List<Map<String, String>> messages) {
        if (!enabled) {
            return objectMapper.createObjectNode();
        }

        try {
            long startTime = System.currentTimeMillis();
            ObjectNode body = buildChatBody(systemPrompt, messages, true, null);

            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    String.class);

            long latencyMs = System.currentTimeMillis() - startTime;

            JsonNode json = objectMapper.readTree(response.getBody());
            if (json.has("choices") && json.get("choices").isArray() && json.get("choices").size() > 0) {
                String content = json.get("choices").get(0).get("message").get("content").asText();

                // Extract tokens
                int inputTokens = 0;
                int outputTokens = 0;
                if (json.has("usage")) {
                    inputTokens = json.get("usage").get("prompt_tokens").asInt();
                    outputTokens = json.get("usage").get("completion_tokens").asInt();
                }

                costTrackingService.recordCost(model, "CHAT_JSON", inputTokens, outputTokens,
                        UUID.randomUUID().toString(), "LlmChatService.chatJson", (int) latencyMs, false, null);

                meterRegistry.counter("nexus.ai.llm.json_success").increment();
                return objectMapper.readTree(content);
            }

            return objectMapper.createObjectNode();

        } catch (Exception e) {
            log.error("OpenAI JSON chat failed", e);
            meterRegistry.counter("nexus.ai.llm.json_error").increment();
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Chat returning full usage metadata for callers that need token counts.
     */
    public Map<String, Object> chatWithUsage(String systemPrompt, List<Map<String, String>> messages) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!enabled) {
            throw unavailable("no model is configured (set nexus.ai.openai.api-key)");
        }

        String fullPrompt = (systemPrompt != null ? systemPrompt : "") + ":" + messages;
        Optional<String> cached = cacheService.get(fullPrompt, model);
        if (cached.isPresent()) {
            result.put("content", cached.get());
            result.put("inputTokens", 0);
            result.put("outputTokens", 0);
            result.put("costUsd", BigDecimal.ZERO);
            result.put("cached", true);
            return result;
        }

        try {
            long startTime = System.currentTimeMillis();
            ObjectNode body = buildChatBody(systemPrompt, messages, false, null);

            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    String.class);

            long latencyMs = System.currentTimeMillis() - startTime;
            JsonNode json = objectMapper.readTree(response.getBody());

            if (json.has("choices") && json.get("choices").isArray() && json.get("choices").size() > 0) {
                String content = json.get("choices").get(0).get("message").get("content").asText();

                int inputTokens = 0;
                int outputTokens = 0;
                if (json.has("usage")) {
                    inputTokens = json.get("usage").get("prompt_tokens").asInt();
                    outputTokens = json.get("usage").get("completion_tokens").asInt();
                }

                BigDecimal cost = costTrackingService.recordCost(model, "CHAT_USAGE", inputTokens, outputTokens,
                        UUID.randomUUID().toString(), "LlmChatService.chatWithUsage", (int) latencyMs, false, null);

                UUID tenantId2 = null;
                try { tenantId2 = TenantContext.getCurrentTenantId(); } catch (IllegalStateException ignored) {}
                cacheService.put(fullPrompt, model, content, tenantId2);

                result.put("content", content);
                result.put("inputTokens", inputTokens);
                result.put("outputTokens", outputTokens);
                result.put("costUsd", cost);
                result.put("cached", false);
                return result;
            }
        } catch (Exception e) {
            log.error("OpenAI chatWithUsage failed", e);
        }

        throw unavailable("the model could not be reached");
    }

    /**
     * Build the request body for OpenAI chat completion.
     */
    private ObjectNode buildChatBody(String systemPrompt, List<Map<String, String>> messages,
                                      boolean jsonMode, Double temperature) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        if (jsonMode) {
            ObjectNode responseFormat = objectMapper.createObjectNode();
            responseFormat.put("type", "json_object");
            body.set("response_format", responseFormat);
        }

        if (temperature != null) {
            body.put("temperature", temperature);
        }

        ArrayNode msgArray = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            msgArray.addObject().put("role", "system").put("content", systemPrompt);
        }
        for (Map<String, String> msg : messages) {
            msgArray.addObject()
                    .put("role", msg.getOrDefault("role", "user"))
                    .put("content", msg.getOrDefault("content", ""));
        }

        return body;
    }

    /**
     * Circuit breaker fallback for chat.
     */
    public String chatFallback(String systemPrompt, List<Map<String, String>> messages, Throwable t) {
        log.warn("LLM chat unavailable (circuit breaker or error): {}", t.getMessage());
        meterRegistry.counter("nexus.ai.llm.circuit_breaker_fallback").increment();
        if (t instanceof AiServiceException ase) throw ase;
        throw unavailable("the model could not be reached");
    }

    /**
     * Circuit breaker fallback for JSON chat.
     */
    public JsonNode chatJsonFallback(String systemPrompt, List<Map<String, String>> messages, Throwable t) {
        log.warn("Circuit breaker OPEN for LLM JSON chat, using fallback: {}", t.getMessage());
        return objectMapper.createObjectNode();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getModel() {
        return model;
    }

    /**
     * What to do when no model can answer: say so. This used to return scripted replies (an invented
     * order number, "sales are up 18%", made-up top customers with dollar amounts), which an operator
     * would read as real business facts. An assistant that cannot answer must not guess.
     */
    private static AiServiceException unavailable(String reason) {
        return new AiServiceException("The AI assistant is not available: " + reason);
    }
}
