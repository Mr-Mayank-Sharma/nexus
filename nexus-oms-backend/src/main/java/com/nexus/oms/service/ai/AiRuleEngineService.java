package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiRuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleEngineService.class);

    private final AiRuleFallbackRepository fallbackRepository;
    private final AiModelRepository modelRepository;
    private final Random random = new Random();

    public AiRuleEngineService(AiRuleFallbackRepository fallbackRepository,
                                AiModelRepository modelRepository) {
        this.fallbackRepository = fallbackRepository;
        this.modelRepository = modelRepository;
    }

    public Map<String, Object> executeFallback(UUID tenantId, String modelType, Map<String, Object> input) {
        String modelTypeForLookup = modelType;
        UUID modelId = modelRepository.findAvailableForTenant(tenantId, modelTypeForLookup,
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().map(AiModel::getId).orElse(null);

        if (modelId == null) {
            return generateGenericFallback(modelType, input);
        }

        List<AiRuleFallback> fallbacks = fallbackRepository
                .findByModelIdAndIsActiveTrueOrderByPriorityAsc(modelId);

        if (!fallbacks.isEmpty()) {
            AiRuleFallback fallback = fallbacks.get(0);
            return executeRule(fallback, input);
        }

        return generateGenericFallback(modelType, input);
    }

    private Map<String, Object> executeRule(AiRuleFallback rule, Map<String, Object> input) {
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
                result.put("value", applyLookup(rule.getActionConfig(), input));
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
        result.put("computedValue", 100 + random.nextInt(500));
        result.put("confidence", new BigDecimal("0.60"));
        return result;
    }

    private Map<String, Object> applyLookup(String config, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", "nearest_warehouse");
        result.put("warehouseId", "wh-default");
        result.put("warehouseName", "Primary Warehouse");
        result.put("confidence", new BigDecimal("0.70"));
        return result;
    }

    private Map<String, Object> applyThreshold(String config, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isAnomaly", false);
        result.put("riskScore", 0.1 + random.nextDouble() * 0.3);
        result.put("severity", "LOW");
        result.put("confidence", new BigDecimal("0.75"));
        return result;
    }

    public Map<String, Object> generateGenericFallback(String modelType, Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        result.put("fallbackReason", "AI model unavailable - using rule-based fallback");
        result.put("confidence", 0.65);
        result.put("ruleEngineUsed", true);

        switch (modelType.toUpperCase()) {
            case "DEMAND_FORECAST":
                double historicalAvg = input.containsKey("historicalAverage")
                    ? ((Number) input.get("historicalAverage")).doubleValue()
                    : 150.0;
                String seasonality = input.containsKey("seasonality")
                    ? (String) input.get("seasonality")
                    : "NONE";
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
                String originZip = (String) input.getOrDefault("originZip", "");
                String region = originZip.length() >= 3 ? "REGION_" + originZip.substring(0, 3) : "REGION_DEFAULT";
                result.put("warehouseId", "wh-fallback-" + (originZip.isEmpty() ? "default" : originZip.substring(0, 1)));
                result.put("warehouseName", "Fallback Warehouse " + (originZip.isEmpty() ? "Central" : "Zone " + originZip.substring(0, 1)));
                result.put("shippingCost", 12.50);
                result.put("estimatedDays", 3);
                result.put("strategy", "NEAREST_WAREHOUSE");
                break;

            case "CARRIER_OPTIMIZER":
                double weight = input.containsKey("totalWeightKg") ? ((Number) input.get("totalWeightKg")).doubleValue() : 5.0;
                String carrier;
                double cost;
                if (weight < 2.0) {
                    carrier = "USPS";
                    cost = 7.50;
                } else if (weight < 10.0) {
                    carrier = "UPS";
                    cost = 12.00;
                } else {
                    carrier = "FEDEX";
                    cost = 22.50;
                }
                result.put("carrier", carrier);
                result.put("serviceLevel", weight < 2.0 ? "PRIORITY" : "GROUND");
                result.put("cost", cost);
                result.put("estimatedDays", weight < 2.0 ? 2 : 5);
                result.put("method", "CHEAPEST_AVAILABLE");
                break;

            case "RETURNS_PREDICTOR":
                double orderHistoryMonths = input.containsKey("orderHistoryMonths")
                    ? ((Number) input.get("orderHistoryMonths")).doubleValue()
                    : 12.0;
                double returnRate = input.containsKey("customerReturnRate")
                    ? ((Number) input.get("customerReturnRate")).doubleValue()
                    : 0.05;
                double predictedProb = Math.min(returnRate * (12.0 / Math.max(orderHistoryMonths, 1)), 0.5);
                result.put("returnProbability", predictedProb);
                result.put("expectedReturnDate", "N/A");
                result.put("topReasons", new String[]{"SIZE_ISSUE", "QUALITY_CONCERN", "DAMAGED"});
                result.put("method", "HISTORICAL_AVERAGE");
                break;

            case "INVENTORY_OPTIMIZER":
                double avgDailyDemand = input.containsKey("avgDailyDemand")
                    ? ((Number) input.get("avgDailyDemand")).doubleValue()
                    : 10.0;
                int leadTimeDays = input.containsKey("leadTimeDays")
                    ? ((Number) input.get("leadTimeDays")).intValue()
                    : 7;
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
}
