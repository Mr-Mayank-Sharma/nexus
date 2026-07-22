package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * SHAP-based explainability service using LLM to generate natural language
 * explanations for AI predictions. Falls back to rule-based explanations
 * when LLM is unavailable.
 */
@Service
public class ShapExplainerService {

    private static final Logger log = LoggerFactory.getLogger(ShapExplainerService.class);

    private final LlmChatService llmChatService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ShapExplainerService(LlmChatService llmChatService,
                                 JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper) {
        this.llmChatService = llmChatService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a natural-language explanation for a prediction.
     *
     * @param modelType  The AI model type (e.g., DEMAND_FORECAST)
     * @param input      The input features that went into the prediction
     * @param output     The prediction result
     * @param tenantId   Tenant context
     * @return Map with "explanation", "shapValues", and "confidence"
     */
    public Map<String, Object> explain(String modelType, Map<String, Object> input,
                                        Map<String, Object> output, UUID tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Try LLM-based explanation
        try {
            String explanation = generateLlmExplanation(modelType, input, output);
            Map<String, Object> shapValues = estimateShapValues(modelType, input);

            result.put("explanation", explanation);
            result.put("shapValues", shapValues);
            result.put("method", "LLM_SHAP");
            result.put("confidence", estimateConfidence(input));

            // Persist explanation
            persistExplanation(modelType, input, output, shapValues, explanation, tenantId);

            return result;
        } catch (Exception e) {
            log.warn("LLM explanation failed, falling back to rule-based: {}", e.getMessage());
        }

        // 2. Fallback: rule-based explanation
        String fallbackExplanation = generateRuleBasedExplanation(modelType, input, output);
        Map<String, Object> shapValues = estimateShapValues(modelType, input);

        result.put("explanation", fallbackExplanation);
        result.put("shapValues", shapValues);
        result.put("method", "RULE_BASED");
        result.put("confidence", 0.6);

        return result;
    }

    /**
     * Use GPT-4o to generate a natural language explanation of the prediction.
     */
    private String generateLlmExplanation(String modelType, Map<String, Object> input,
                                           Map<String, Object> output) {
        String systemPrompt = """
            You are an AI explainability expert for a supply chain management system.
            Generate a clear, concise natural language explanation (2-4 sentences) of an AI prediction.
            Focus on: what factors drove the prediction, the confidence level, and any actionable insights.
            Be specific about numbers and features. Do NOT use markdown formatting.
            """;

        String userPrompt = String.format(
            "Model type: %s\n\nInput features:\n%s\n\nPrediction output:\n%s\n\nExplain this prediction:",
            modelType, formatMap(input), formatMap(output));

        var messages = List.of(Map.of("role", "user", "content", userPrompt));
        return llmChatService.chat(systemPrompt, messages);
    }

    /**
     * Estimate SHAP-like feature importance scores.
     * Uses heuristic-based attribution for each model type.
     */
    private Map<String, Object> estimateShapValues(String modelType, Map<String, Object> input) {
        Map<String, Object> shapValues = new LinkedHashMap<>();

        switch (modelType) {
            case "DEMAND_FORECAST" -> {
                shapValues.put("historicalAverage", 0.35);
                shapValues.put("seasonality", 0.25);
                shapValues.put("trend", 0.20);
                shapValues.put("dayOfWeek", 0.10);
                shapValues.put("promotionActive", 0.10);
            }
            case "SMART_ALLOCATOR" -> {
                shapValues.put("originZip", 0.30);
                shapValues.put("destZip", 0.30);
                shapValues.put("weightKg", 0.20);
                shapValues.put("declaredValue", 0.20);
            }
            case "CARRIER_OPTIMIZER" -> {
                shapValues.put("totalWeightKg", 0.35);
                shapValues.put("destination", 0.25);
                shapValues.put("declaredValue", 0.15);
                shapValues.put("urgency", 0.15);
                shapValues.put("preferredCarrier", 0.10);
            }
            case "RETURNS_PREDICTOR" -> {
                shapValues.put("customerReturnRate", 0.30);
                shapValues.put("productCategory", 0.25);
                shapValues.put("orderValue", 0.20);
                shapValues.put("orderHistoryMonths", 0.15);
                shapValues.put("sizeProvided", 0.10);
            }
            case "ANOMALY_DETECTOR" -> {
                shapValues.put("orderValue", 0.25);
                shapValues.put("itemCount", 0.20);
                shapValues.put("shippingAddressChanged", 0.20);
                shapValues.put("newCustomer", 0.20);
                shapValues.put("paymentMethodChanged", 0.15);
            }
            default -> {
                shapValues.put("unknown", 1.0);
            }
        }

        // Adjust based on which features are actually present
        Map<String, Object> adjusted = new LinkedHashMap<>();
        double totalWeight = shapValues.values().stream()
            .mapToDouble(v -> ((Number) v).doubleValue()).sum();

        for (Map.Entry<String, Object> entry : shapValues.entrySet()) {
            boolean present = input.containsKey(entry.getKey()) && input.get(entry.getKey()) != null;
            double baseWeight = ((Number) entry.getValue()).doubleValue();
            double adjustedWeight = present ? baseWeight / totalWeight : 0.0;
            adjusted.put(entry.getKey(), Math.round(adjustedWeight * 1000.0) / 1000.0);
        }

        return adjusted;
    }

    /**
     * Generate a rule-based explanation without LLM.
     */
    private String generateRuleBasedExplanation(String modelType, Map<String, Object> input,
                                                 Map<String, Object> output) {
        return switch (modelType) {
            case "DEMAND_FORECAST" -> {
                double avg = input.containsKey("historicalAverage")
                    ? ((Number) input.get("historicalAverage")).doubleValue() : 0;
                yield String.format(
                    "Forecast generated based on historical average of %.0f units. " +
                    "Seasonality and trend adjustments applied. Confidence: moderate.",
                    avg);
            }
            case "CARRIER_OPTIMIZER" -> {
                String carrier = output.containsKey("carrier") ? output.get("carrier").toString() : "best available";
                double cost = output.containsKey("cost") ? ((Number) output.get("cost")).doubleValue() : 0;
                yield String.format(
                    "Selected %s as optimal carrier based on weight, destination, and cost. " +
                    "Estimated cost: $%.2f.", carrier, cost);
            }
            case "ANOMALY_DETECTOR" -> {
                boolean isAnomaly = output.containsKey("isAnomaly") &&
                    Boolean.parseBoolean(output.get("isAnomaly").toString());
                yield isAnomaly
                    ? "Anomaly detected based on order value, item count, and behavioral signals. Manual review recommended."
                    : "Order appears normal based on historical patterns and behavioral analysis.";
            }
            default -> String.format("Prediction generated for %s model type using available input features.", modelType);
        };
    }

    private double estimateConfidence(Map<String, Object> input) {
        long providedFields = input.values().stream().filter(Objects::nonNull).count();
        return Math.min(0.5 + providedFields * 0.12, 0.95);
    }

    private void persistExplanation(String modelType, Map<String, Object> input,
                                     Map<String, Object> output, Map<String, Object> shapValues,
                                     String explanation, UUID tenantId) {
        try {
            jdbcTemplate.update(
                "INSERT INTO ai_shap_explanations " +
                "(tenant_id, model_type, input_json, output_json, shap_values, explanation, confidence) " +
                "VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)",
                tenantId, modelType,
                objectMapper.writeValueAsString(input),
                objectMapper.writeValueAsString(output),
                objectMapper.writeValueAsString(shapValues),
                explanation,
                estimateConfidence(input));
        } catch (Exception e) {
            log.warn("Failed to persist SHAP explanation: {}", e.getMessage());
        }
    }

    private String formatMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "(empty)";
        var sb = new StringBuilder();
        for (var entry : map.entrySet()) {
            sb.append(String.format("  - %s: %s%n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }
}
