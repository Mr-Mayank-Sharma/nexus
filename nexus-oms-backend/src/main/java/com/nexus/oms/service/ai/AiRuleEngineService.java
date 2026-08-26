package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiRuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleEngineService.class);

    private final AiRuleFallbackRepository fallbackRepository;
    private final AiModelRepository modelRepository;
    private final LlmChatService llmChatService;
    private final AiWarehouseResolutionService warehouseResolutionService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AiRuleEngineService(AiRuleFallbackRepository fallbackRepository,
                                AiModelRepository modelRepository,
                                LlmChatService llmChatService,
                                AiWarehouseResolutionService warehouseResolutionService,
                                ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.fallbackRepository = fallbackRepository;
        this.modelRepository = modelRepository;
        this.llmChatService = llmChatService;
        this.warehouseResolutionService = warehouseResolutionService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Execute fallback logic. Tries LLM-enhanced fallback first, falls back to DB rules.
     */
    public Map<String, Object> executeFallback(UUID tenantId, String modelType, Map<String, Object> input) {
        // Try LLM-enhanced fallback first
        if (llmChatService.isEnabled()) {
            try {
                Map<String, Object> llmResult = llmEnhancedFallback(modelType, input);
                if (llmResult != null && !llmResult.isEmpty()) {
                    llmResult.put("fallbackUsed", true);
                    llmResult.put("fallbackSource", "LLM_ENHANCED");
                    meterRegistry.counter("nexus.ai.rule_engine.llm_fallback_success").increment();
                    return llmResult;
                }
            } catch (Exception e) {
                log.warn("LLM-enhanced fallback failed for {}: {}", modelType, e.getMessage());
                meterRegistry.counter("nexus.ai.rule_engine.llm_fallback_error").increment();
            }
        }

        // Try DB-configured rules
        UUID modelId = modelRepository.findAvailableForTenant(tenantId, modelType,
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().map(AiModel::getId).orElse(null);

        if (modelId != null) {
            List<AiRuleFallback> fallbacks = fallbackRepository
                    .findByModelIdAndIsActiveTrueOrderByPriorityAsc(modelId);
            if (!fallbacks.isEmpty()) {
                Map<String, Object> ruleResult = executeRule(tenantId, fallbacks.get(0), input);
                ruleResult.put("fallbackSource", "DB_RULE");
                return ruleResult;
            }
        }

        // Final fallback: hardcoded rules
        Map<String, Object> genericResult = generateGenericFallback(tenantId, modelType, input);
        genericResult.put("fallbackSource", "HARDCODED_RULE");
        return genericResult;
    }

    /**
     * Use LLM to generate a smarter fallback prediction.
     */
    private Map<String, Object> llmEnhancedFallback(String modelType, Map<String, Object> input) {
        String systemPrompt = """
            You are a supply chain AI fallback system. The primary ML model is unavailable.
            Given the input features, provide a reasonable prediction using your knowledge of
            supply chain operations. Be conservative in your estimates.
            Return a JSON object with appropriate fields for the model type.
            """;

        String userPrompt = String.format(
            "Model type: %s\nInput features:\n%s\n\nProvide a conservative fallback prediction as JSON:",
            modelType, formatInput(input));

        var messages = List.of(Map.of("role", "user", "content", userPrompt));
        JsonNode llmResult = llmChatService.chatJson(systemPrompt, messages);

        if (llmResult == null || llmResult.isEmpty()) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        llmResult.fields().forEachRemaining(entry -> {
            result.put(entry.getKey(), convertJsonNode(entry.getValue()));
        });
        result.put("confidence", 0.70);
        return result;
    }

    private Map<String, Object> executeRule(UUID tenantId, AiRuleFallback rule, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fallbackUsed", true);
        result.put("fallbackRule", rule.getName());
        result.put("fallbackType", rule.getActionType());
        result.put("timestamp", LocalDateTime.now().toString());

        switch (rule.getActionType()) {
            case "FORMULA":
                result.put("value", applyFormula(rule.getActionConfig(), input));
                break;
            case "LOOKUP_TABLE":
                result.put("value", applyLookup(tenantId, rule.getActionConfig(), input));
                break;
            case "THRESHOLD_RULE":
                result.put("value", applyThreshold(rule.getActionConfig(), input));
                break;
            case "STATIC_VALUE":
                result.put("value", rule.getActionConfig());
                break;
            default:
                result.put("value", "fallback_default");
        }
        return result;
    }

    private Map<String, Object> applyFormula(String config, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", "historical_average");
        result.put("lookbackDays", 90);
        result.put("computedValue", computeFormulaValue(config, input));
        result.put("confidence", new BigDecimal("0.60"));
        return result;
    }

    private double computeFormulaValue(String config, Map<String, Object> input) {
        double multiplier = 1.0;
        Double base = null;
        if (config != null && !config.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(config);
                if (node.has("multiplier") && node.get("multiplier").isNumber()) {
                    multiplier = node.get("multiplier").asDouble(1.0);
                }
                if (node.has("baseValue") && node.get("baseValue").isNumber()) {
                    base = node.get("baseValue").asDouble();
                }
                if (base == null && node.has("feature") && node.get("feature").isTextual()) {
                    String feature = node.get("feature").asText();
                    if (input.get(feature) instanceof Number) {
                        base = ((Number) input.get(feature)).doubleValue();
                    }
                }
            } catch (Exception e) {
                log.debug("Unparseable formula config '{}', falling back to input-derived value", config);
            }
        }
        if (base == null && input.get("historicalAverage") instanceof Number) {
            base = ((Number) input.get("historicalAverage")).doubleValue();
        }
        if (base == null) {
            base = 100.0;
        }
        return Math.round(base * multiplier * 100.0) / 100.0;
    }

    private Map<String, Object> applyLookup(UUID tenantId, String config, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", "nearest_warehouse");
        // Resolve against the tenant's REAL warehouse network — never invent IDs.
        java.util.Optional<Warehouse> resolved = warehouseResolutionService.primaryForTenant(tenantId);
        if (resolved.isPresent()) {
            Warehouse wh = resolved.get();
            result.put("warehouseId", wh.getId().toString());
            result.put("warehouseName", wh.getName());
        } else {
            result.put("warehouseId", null);
            result.put("warehouseName", null);
            result.put("warehouseNote", "No active warehouse configured for this tenant");
        }
        result.put("confidence", new BigDecimal("0.70"));
        return result;
    }

    private Map<String, Object> applyThreshold(String config, Map<String, Object> input) {
        double threshold = 0.5;
        if (config != null && !config.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(config);
                if (node.has("threshold") && node.get("threshold").isNumber()) {
                    threshold = node.get("threshold").asDouble(0.5);
                }
            } catch (Exception e) {
                log.debug("Unparseable threshold config '{}', using default threshold", config);
            }
        }

        double orderValue = input.get("orderValue") instanceof Number
                ? ((Number) input.get("orderValue")).doubleValue() : 0;
        int itemCount = input.get("itemCount") instanceof Number
                ? ((Number) input.get("itemCount")).intValue() : 1;

        double riskScore = Math.min(1.0, (orderValue / 10000.0) * 0.7 + (Math.min(itemCount, 50) / 50.0) * 0.3);
        riskScore = Math.round(riskScore * 100.0) / 100.0;
        boolean isAnomaly = riskScore >= threshold;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isAnomaly", isAnomaly);
        result.put("riskScore", riskScore);
        result.put("severity", isAnomaly ? (riskScore >= 0.8 ? "HIGH" : "MEDIUM") : "LOW");
        result.put("confidence", new BigDecimal("0.75"));
        return result;
    }

    public Map<String, Object> generateGenericFallback(UUID tenantId, String modelType, Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        result.put("fallbackReason", "AI model unavailable - using rule-based fallback");
        result.put("confidence", 0.65);
        result.put("ruleEngineUsed", true);

        switch (modelType.toUpperCase()) {
            case "DEMAND_FORECAST":
                double historicalAvg = input.containsKey("historicalAverage")
                    ? ((Number) input.get("historicalAverage")).doubleValue() : 150.0;
                String seasonality = input.containsKey("seasonality")
                    ? (String) input.get("seasonality") : "NONE";
                double seasonalMultiplier = switch (seasonality.toUpperCase()) {
                    case "HIGH" -> 1.3;
                    case "MEDIUM" -> 1.15;
                    default -> 1.0;
                };
                result.put("predictedOrders", (int) Math.round(historicalAvg * seasonalMultiplier));
                result.put("lowerBound", (int) Math.round(historicalAvg * seasonalMultiplier * 0.85));
                result.put("upperBound", (int) Math.round(historicalAvg * seasonalMultiplier * 1.15));
                result.put("unit", "orders");
                result.put("method", "MOVING_AVERAGE");
                result.put("lookbackDays", 90);
                break;

            case "SMART_ALLOCATOR":
                String destZip = (String) input.getOrDefault("destZip", "");
                // Resolve against the tenant's REAL warehouse network — never invent IDs.
                java.util.Optional<Warehouse> resolvedWh =
                        warehouseResolutionService.resolveForDestination(tenantId, destZip);
                if (resolvedWh.isPresent()) {
                    Warehouse wh = resolvedWh.get();
                    result.put("warehouseId", wh.getId().toString());
                    result.put("warehouseName", wh.getName());
                } else {
                    result.put("warehouseId", null);
                    result.put("warehouseName", null);
                    result.put("warehouseNote", "No active warehouse configured for this tenant");
                }
                result.put("shippingCost", 12.50);
                result.put("estimatedDays", 3);
                result.put("strategy", "NEAREST_WAREHOUSE");
                break;

            case "CARRIER_OPTIMIZER":
                double weight = input.containsKey("totalWeightKg") ? ((Number) input.get("totalWeightKg")).doubleValue() : 5.0;
                String carrier;
                double cost;
                if (weight < 2.0) { carrier = "USPS"; cost = 7.50; }
                else if (weight < 10.0) { carrier = "UPS"; cost = 12.00; }
                else { carrier = "FEDEX"; cost = 22.50; }
                result.put("carrier", carrier);
                result.put("serviceLevel", weight < 2.0 ? "PRIORITY" : "GROUND");
                result.put("cost", cost);
                result.put("estimatedDays", weight < 2.0 ? 2 : 5);
                result.put("method", "CHEAPEST_AVAILABLE");
                break;

            case "RETURNS_PREDICTOR":
                double orderHistoryMonths = input.containsKey("orderHistoryMonths")
                    ? ((Number) input.get("orderHistoryMonths")).doubleValue() : 12.0;
                double returnRate = input.containsKey("customerReturnRate")
                    ? ((Number) input.get("customerReturnRate")).doubleValue() : 0.05;
                double predictedProb = Math.min(returnRate * (12.0 / Math.max(orderHistoryMonths, 1)), 0.5);
                result.put("returnProbability", predictedProb);
                result.put("expectedReturnDate", "N/A");
                result.put("topReasons", new String[]{"SIZE_ISSUE", "QUALITY_CONCERN", "DAMAGED"});
                result.put("method", "HISTORICAL_AVERAGE");
                break;

            case "INVENTORY_OPTIMIZER":
                double avgDailyDemand = input.containsKey("avgDailyDemand")
                    ? ((Number) input.get("avgDailyDemand")).doubleValue() : 10.0;
                int leadTimeDays = input.containsKey("leadTimeDays")
                    ? ((Number) input.get("leadTimeDays")).intValue() : 7;
                int safetyStock = (int) Math.round(avgDailyDemand * leadTimeDays * 0.3);
                int reorderPoint = (int) Math.round(avgDailyDemand * leadTimeDays) + safetyStock;
                result.put("reorderPoint", reorderPoint);
                result.put("safetyStock", safetyStock);
                result.put("reorderQty", (int) Math.round(avgDailyDemand * leadTimeDays * 1.5));
                result.put("riskLevel", safetyStock < 50 ? "HIGH" : "LOW");
                result.put("method", "MIN_MAX_FORMULA");
                break;

            case "ANOMALY_DETECTOR":
                double orderValue = input.containsKey("orderValue") ? ((Number) input.get("orderValue")).doubleValue() : 0;
                int itemCount = input.containsKey("itemCount") ? ((Number) input.get("itemCount")).intValue() : 1;
                boolean isAnomaly = orderValue > 10000 || itemCount > 50;
                result.put("isAnomaly", isAnomaly);
                result.put("anomalyScore", isAnomaly ? 0.85 : 0.05);
                result.put("severity", isAnomaly ? "HIGH" : "NONE");
                result.put("flags", isAnomaly ? new String[]{"HIGH_VALUE", "BULK_ORDER"} : new String[0]);
                result.put("method", "THRESHOLD_RULE");
                break;

            default:
                result.put("prediction", "Rule-based prediction");
                result.put("method", "GENERIC_RULE");
                result.put("confidence", 0.50);
        }
        return result;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Object convertJsonNode(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isNull()) return null;
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(e -> list.add(convertJsonNode(e)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), convertJsonNode(e.getValue())));
            return map;
        }
        return node.asText();
    }

    private String formatInput(Map<String, Object> input) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            sb.append(String.format("  - %s: %s%n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }
}
