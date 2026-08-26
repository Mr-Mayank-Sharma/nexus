package com.nexus.oms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.dto.InventoryRecommendation;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Map;

/**
 * Legacy bridge to the Python Flask model servers (:5000 ops / :5001 intel).
 *
 * P1.3 contract: this bridge NEVER fabricates results. When the Flask
 * backend is down or malformed, it throws — the controller surfaces a 503.
 * Silent "STANDARD"/"FALLBACK" responses are gone: callers must never
 * mistake fabricated output for a real prediction.
 *
 * The @CircuitBreaker (no fallbackMethod) fast-fails after repeated
 * failures instead of piling up 30s timeouts.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String baseUrlOps;
    private final String baseUrlIntel;

    public AiService(@Value("${nexus.ai.base-url-ops}") String baseUrlOps,
                     @Value("${nexus.ai.base-url-intel}") String baseUrlIntel,
                     @Value("${nexus.ai.timeout-ms:30000}") int timeoutMs,
                     MeterRegistry meterRegistry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        this.meterRegistry = meterRegistry;
        this.baseUrlOps = baseUrlOps;
        this.baseUrlIntel = baseUrlIntel;
    }

    @CircuitBreaker(name = "ai-service")
    public com.nexus.oms.dto.AllocationResponse callCarrierAi(Map<String, Object> input) {
        try {
            JsonNode json = postForJson(baseUrlOps + "/api/predict/carrier", input);
            String carrier = json.path("carrier").asText("");
            if (carrier.isBlank()) {
                throw new IllegalStateException("Legacy AI carrier response missing 'carrier' field");
            }
            return com.nexus.oms.dto.AllocationResponse.builder().carrier(carrier).build();
        } catch (Exception e) {
            recordError("carrier", e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "ai-service")
    public DemandForecastResponse callDemandAi(Map<String, Object> input) {
        try {
            JsonNode json = postForJson(baseUrlIntel + "/api/predict/demand", input);
            if (!json.has("next_7_days") && !json.has("next_30_days")) {
                throw new IllegalStateException("Legacy AI demand response missing forecast fields");
            }
            return DemandForecastResponse.builder()
                    .next7Days(Map.of("total", json.path("next_7_days").asInt(0)))
                    .next30Days(Map.of("total", json.path("next_30_days").asInt(0)))
                    .confidence(DemandForecastResponse.ConfidenceInterval.builder()
                            .lower(json.path("confidence_interval").path("p10").asDouble(0.0))
                            .upper(json.path("confidence_interval").path("p90").asDouble(0.0))
                            .build())
                    .build();
        } catch (Exception e) {
            recordError("demand", e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "ai-service")
    public InventoryRecommendation callInventoryAi(Map<String, Object> input) {
        try {
            JsonNode json = postForJson(baseUrlIntel + "/api/predict/inventory", input);
            if (!json.has("needs_reorder")) {
                throw new IllegalStateException("Legacy AI inventory response missing 'needs_reorder'");
            }
            return InventoryRecommendation.builder()
                    .needsReorder(json.path("needs_reorder").asBoolean(false))
                    .recommendedQty(json.path("recommended_qty").asInt(0))
                    .confidence(json.path("confidence").asDouble(0.0))
                    .build();
        } catch (Exception e) {
            recordError("inventory", e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        }
    }

    private JsonNode postForJson(String url, Map<String, Object> input) {
        String response = restTemplate.postForObject(url, input, String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Empty response from legacy AI service at " + url);
        }
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable response from legacy AI service at " + url);
        }
    }

    private void recordError(String endpoint, Exception e) {
        log.warn("Legacy AI bridge failure [{}]: {}", endpoint, e.getMessage());
        meterRegistry.counter("nexus.ai.legacy_bridge.errors", "endpoint", endpoint).increment();
    }
}
