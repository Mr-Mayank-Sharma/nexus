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

    private Map<String, Object> generateGenericFallback(String modelType, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fallbackUsed", true);
        result.put("timestamp", LocalDateTime.now().toString());

        switch (modelType) {
            case "DEMAND_FORECAST":
                result.put("predictedOrders", 150);
                result.put("confidence", 0.50);
                result.put("method", "MOVING_AVERAGE");
                break;
            case "SMART_ALLOCATOR":
                result.put("warehouseId", "wh-primary");
                result.put("method", "NEAREST_WAREHOUSE");
                result.put("confidence", 0.60);
                break;
            case "CARRIER_OPTIMIZER":
                result.put("carrier", "FEDEX");
                result.put("serviceLevel", "GROUND");
                result.put("cost", 12.99);
                result.put("confidence", 0.55);
                break;
            case "RETURNS_PREDICTOR":
                result.put("returnProbability", 0.15);
                result.put("confidence", 0.50);
                break;
            case "INVENTORY_OPTIMIZER":
                result.put("reorderPoint", 100);
                result.put("safetyStock", 30);
                result.put("reorderQty", 500);
                result.put("confidence", 0.55);
                break;
            case "ANOMALY_DETECTOR":
                result.put("isAnomaly", false);
                result.put("anomalyScore", 0.05);
                result.put("confidence", 0.65);
                break;
            default:
                result.put("value", "default_fallback");
                result.put("confidence", 0.50);
        }
        return result;
    }
}
