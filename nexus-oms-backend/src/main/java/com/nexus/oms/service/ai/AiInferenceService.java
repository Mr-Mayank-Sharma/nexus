package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import com.nexus.oms.security.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiInferenceService {

    private static final Logger log = LoggerFactory.getLogger(AiInferenceService.class);

    private final AiModelRepository modelRepository;
    private final AiModelVersionRepository versionRepository;
    private final AiRuleFallbackRepository fallbackRepository;
    private final AiInferenceLogRepository inferenceLogRepository;
    private final LlmChatService llmChatService;
    private final ShapExplainerService shapExplainerService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AiInferenceService(AiModelRepository modelRepository,
                               AiModelVersionRepository versionRepository,
                               AiRuleFallbackRepository fallbackRepository,
                               AiInferenceLogRepository inferenceLogRepository,
                               LlmChatService llmChatService,
                               ShapExplainerService shapExplainerService,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.fallbackRepository = fallbackRepository;
        this.inferenceLogRepository = inferenceLogRepository;
        this.llmChatService = llmChatService;
        this.shapExplainerService = shapExplainerService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public Map<String, Object> execute(UUID modelId, UUID versionId, Map<String, Object> input) {
        AiModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new NoSuchElementException("Model not found"));
        AiModelVersion version = versionRepository.findById(versionId).orElse(null);

        return generatePrediction(model, version, input);
    }

    public Map<String, Object> generatePrediction(AiModel model, AiModelVersion version, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelName", model.getName());
        result.put("modelVersion", version != null ? version.getVersion() : "unknown");
        result.put("timestamp", LocalDateTime.now().toString());

        String modelType = model.getModelType();
        boolean llmSuccess = false;

        // Try LLM-based prediction first
        if (llmChatService.isEnabled()) {
            try {
                result = llmPredict(modelType, input, result);
                llmSuccess = true;
                meterRegistry.counter("nexus.ai.inference.llm_success", "type", modelType).increment();
            } catch (Exception e) {
                log.warn("LLM prediction failed for {}, falling back to rules: {}", modelType, e.getMessage());
                meterRegistry.counter("nexus.ai.inference.llm_fallback", "type", modelType).increment();
            }
        }

        // Fall back to hardcoded rules
        if (!llmSuccess) {
            result = ruleBasedPredict(modelType, input, result);
        }

        // Generate SHAP explanation
        try {
            Map<String, Object> explanation = shapExplainerService.explain(modelType, input, result, null);
            result.put("explanation", explanation.get("explanation"));
            result.put("shapValues", explanation.get("shapValues"));
            result.put("explanationMethod", explanation.get("method"));
        } catch (Exception e) {
            log.debug("SHAP explanation failed: {}", e.getMessage());
        }

        result.put("llmPowered", llmSuccess);
        return result;
    }

    // ============================================================
    // LLM-based prediction for each model type
    // ============================================================

    private Map<String, Object> llmPredict(String modelType, Map<String, Object> input,
                                            Map<String, Object> result) {
        String systemPrompt = switch (modelType) {
            case "DEMAND_FORECAST" -> """
                You are a supply chain demand forecasting AI. Given the input features, predict order volume.
                Return a JSON object with keys: predictedOrders (double), lowerBound (double), upperBound (double),
                confidence (double 0-1), explanation (string). Use the input data to make an informed prediction.
                """;
            case "SMART_ALLOCATOR" -> """
                You are a warehouse allocation AI. Given origin/destination and weight, select the best warehouse.
                Return JSON with: warehouseId (string), warehouseName (string), shippingCost (double),
                estimatedDays (int), confidence (double 0-1), explanation (string).
                """;
            case "CARRIER_OPTIMIZER" -> """
                You are a carrier optimization AI. Given shipment details, select the best carrier and service level.
                Return JSON with: carrier (string), serviceLevel (string), cost (double),
                estimatedDelivery (string date), confidence (double 0-1), explanation (string).
                """;
            case "RETURNS_PREDICTOR" -> """
                You are a returns prediction AI. Given order and customer features, predict return probability.
                Return JSON with: returnProbability (double 0-1), expectedReturnDate (string date),
                topReasons (string array), confidence (double 0-1), explanation (string).
                """;
            case "INVENTORY_OPTIMIZER" -> """
                You are an inventory optimization AI. Given demand and stock data, compute reorder parameters.
                Return JSON with: reorderPoint (int), safetyStock (int), reorderQty (int),
                riskLevel (CRITICAL/HIGH/MEDIUM/LOW), confidence (double 0-1), explanation (string).
                """;
            case "ANOMALY_DETECTOR" -> """
                You are an order anomaly detection AI. Given order features, detect anomalies.
                Return JSON with: isAnomaly (boolean), anomalyScore (double 0-1), severity (CRITICAL/HIGH/MEDIUM/NONE),
                flaggedReasons (string array), confidence (double 0-1), explanation (string).
                """;
            default -> """
                You are a supply chain AI assistant. Analyze the input and provide a prediction.
                Return JSON with: prediction (string), confidence (double 0-1), explanation (string).
                """;
        };

        try {
            String inputJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
            var messages = List.of(Map.of("role", "user", "content", "Input features:\n" + inputJson));

            JsonNode llmResult = llmChatService.chatJson(systemPrompt, messages);

            if (llmResult != null && !llmResult.isEmpty()) {
                // Merge LLM result into the output map
                llmResult.fields().forEachRemaining(entry ->
                    result.put(entry.getKey(), deserializeJsonNode(entry.getValue()))
                );
            }
        } catch (Exception e) {
            log.warn("LLM inference failed, using rule-based fallback: {}", e.getMessage());
        }

        return result;
    }

    // ============================================================
    // Rule-based prediction (existing logic, preserved as fallback)
    // ============================================================

    private Map<String, Object> ruleBasedPredict(String modelType, Map<String, Object> input,
                                                  Map<String, Object> result) {
        switch (modelType) {
            case "DEMAND_FORECAST" -> predictDemandForecast(input, result);
            case "SMART_ALLOCATOR" -> predictSmartAllocator(input, result);
            case "CARRIER_OPTIMIZER" -> predictCarrierOptimizer(input, result);
            case "RETURNS_PREDICTOR" -> predictReturns(input, result);
            case "INVENTORY_OPTIMIZER" -> predictInventory(input, result);
            case "ANOMALY_DETECTOR" -> predictAnomaly(input, result);
            case "AI_ASSISTANT" -> predictAssistant(input, result);
            case "DOCUMENT_AI" -> predictDocumentAi(input, result);
            default -> {
                long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);
                result.put("prediction", "unknown");
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", "Unrecognized model type: " + modelType);
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
            }
        }
        return result;
    }

    private void predictDemandForecast(Map<String, Object> input, Map<String, Object> result) {
        double historicalAverage = input.containsKey("historicalAverage")
                ? ((Number) input.get("historicalAverage")).doubleValue() : 100.0;
        String seasonality = input.containsKey("seasonality")
                ? input.get("seasonality").toString().toUpperCase() : "MEDIUM";
        String trend = input.containsKey("trend")
                ? input.get("trend").toString().toUpperCase() : "STABLE";

        double seasonalityMultiplier = switch (seasonality) {
            case "HIGH" -> 1.3;
            case "MEDIUM" -> 1.15;
            case "LOW" -> 1.05;
            default -> 1.0;
        };
        double trendFactor = switch (trend) {
            case "UP" -> 1.1;
            case "DOWN" -> 0.9;
            default -> 1.0;
        };

        double predictedOrders = historicalAverage * seasonalityMultiplier * trendFactor;
        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.15, 0.99);

        result.put("predictedOrders", Math.round(predictedOrders * 100.0) / 100.0);
        result.put("lowerBound", Math.round(predictedOrders * 0.85 * 100.0) / 100.0);
        result.put("upperBound", Math.round(predictedOrders * 1.15 * 100.0) / 100.0);
        result.put("unit", "units");
        result.put("prediction", Math.round(predictedOrders * 100.0) / 100.0);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictSmartAllocator(Map<String, Object> input, Map<String, Object> result) {
        String originZip = input.containsKey("originZip") ? input.get("originZip").toString() : "00000";
        String destZip = input.containsKey("destZip") ? input.get("destZip").toString() : "00000";
        double weightKg = input.containsKey("weightKg") ? ((Number) input.get("weightKg")).doubleValue() : 1.0;
        double declaredValue = input.containsKey("declaredValue") ? ((Number) input.get("declaredValue")).doubleValue() : 0.0;

        String originRegion = getRegionFromZip(originZip);
        String destRegion = getRegionFromZip(destZip);
        String warehouseId = getNearestWarehouse(originRegion, destRegion);
        String warehouseName = getWarehouseName(warehouseId);

        int zoneDistance = getZoneDistance(originRegion, destRegion);
        double shippingCost = 3.50 + weightKg * 0.45 + zoneDistance * 0.30 + declaredValue * 0.002;
        int estimatedDays = 1 + zoneDistance;

        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);

        result.put("warehouseId", warehouseId);
        result.put("warehouseName", warehouseName);
        result.put("shippingCost", Math.round(shippingCost * 100.0) / 100.0);
        result.put("estimatedDays", estimatedDays);
        result.put("prediction", warehouseName);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictCarrierOptimizer(Map<String, Object> input, Map<String, Object> result) {
        double totalWeightKg = input.containsKey("totalWeightKg") ? ((Number) input.get("totalWeightKg")).doubleValue() : 1.0;
        double declaredValue = input.containsKey("declaredValue") ? ((Number) input.get("declaredValue")).doubleValue() : 0.0;
        String destination = input.containsKey("destination") ? input.get("destination").toString() : "UNKNOWN";

        String carrier;
        String serviceLevel;
        double baseCost;

        if (totalWeightKg < 2.0) {
            carrier = "USPS"; serviceLevel = "GROUND"; baseCost = 3.99 + totalWeightKg * 0.80;
        } else if (totalWeightKg <= 10.0) {
            carrier = "UPS"; serviceLevel = totalWeightKg > 5.0 ? "GROUND" : "2_DAY"; baseCost = 7.99 + totalWeightKg * 0.65;
        } else {
            carrier = "FEDEX"; serviceLevel = "EXPRESS"; baseCost = 14.99 + totalWeightKg * 0.50;
        }

        double cost = baseCost + declaredValue * 0.005;
        int deliveryDays = switch (serviceLevel) {
            case "OVERNIGHT" -> 1;
            case "2_DAY" -> 2;
            case "EXPRESS" -> 3;
            default -> 5;
        };

        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.15, 0.99);

        result.put("carrier", carrier);
        result.put("serviceLevel", serviceLevel);
        result.put("cost", Math.round(cost * 100.0) / 100.0);
        result.put("estimatedDelivery", LocalDate.now().plusDays(deliveryDays).toString());
        result.put("prediction", carrier + " " + serviceLevel);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictReturns(Map<String, Object> input, Map<String, Object> result) {
        double customerReturnRate = input.containsKey("customerReturnRate") ? ((Number) input.get("customerReturnRate")).doubleValue() : 0.05;
        int orderHistoryMonths = input.containsKey("orderHistoryMonths") ? ((Number) input.get("orderHistoryMonths")).intValue() : 1;
        String productCategory = input.containsKey("productCategory") ? input.get("productCategory").toString().toUpperCase() : "GENERAL";
        double orderValue = input.containsKey("orderValue") ? ((Number) input.get("orderValue")).doubleValue() : 50.0;

        double categoryRisk = switch (productCategory) {
            case "ELECTRONICS" -> 1.4;
            case "CLOTHING" -> 1.6;
            case "FOOD" -> 1.1;
            case "BOOKS" -> 0.6;
            case "FURNITURE" -> 1.3;
            default -> 1.0;
        };

        double valueFactor = Math.min(orderValue / 200.0, 1.5);
        double tenureFactor = Math.max(1.0 - (orderHistoryMonths - 1) * 0.02, 0.7);
        double returnProbability = Math.min(customerReturnRate * categoryRisk * valueFactor * tenureFactor, 0.5);

        List<String> topReasons = switch (productCategory) {
            case "CLOTHING" -> List.of("SIZE_ISSUE", "STYLE_MISMATCH", "QUALITY_CONCERN");
            case "ELECTRONICS" -> List.of("DEFECTIVE", "NOT_AS_DESCRIBED", "COMPATIBILITY_ISSUE");
            case "FOOD" -> List.of("EXPIRY_CONCERN", "DAMAGED_PACKAGING", "QUALITY_CONCERN");
            case "FURNITURE" -> List.of("DAMAGED_IN_TRANSIT", "ASSEMBLY_ISSUE", "COLOR_MISMATCH");
            default -> List.of("SIZE_ISSUE", "QUALITY_CONCERN", "DAMAGED");
        };

        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);

        result.put("returnProbability", Math.round(returnProbability * 10000.0) / 10000.0);
        result.put("expectedReturnDate", LocalDate.now().plusDays(7 + (int) Math.round(orderHistoryMonths * 0.5)).toString());
        result.put("topReasons", topReasons);
        result.put("prediction", Math.round(returnProbability * 10000.0) / 10000.0);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictInventory(Map<String, Object> input, Map<String, Object> result) {
        double avgDailyDemand = input.containsKey("avgDailyDemand") ? ((Number) input.get("avgDailyDemand")).doubleValue() : 10.0;
        int leadTimeDays = input.containsKey("leadTimeDays") ? ((Number) input.get("leadTimeDays")).intValue() : 7;
        int currentStock = input.containsKey("currentStock") ? ((Number) input.get("currentStock")).intValue() : 100;

        double reorderPoint = avgDailyDemand * leadTimeDays * 1.3;
        double safetyStock = avgDailyDemand * leadTimeDays * 0.3;
        double reorderQty = avgDailyDemand * leadTimeDays * 2.0;
        double stockCoverageDays = currentStock / Math.max(avgDailyDemand, 0.1);

        String riskLevel;
        if (stockCoverageDays < leadTimeDays * 0.5) riskLevel = "CRITICAL";
        else if (stockCoverageDays < leadTimeDays) riskLevel = "HIGH";
        else if (stockCoverageDays < leadTimeDays * 1.5) riskLevel = "MEDIUM";
        else riskLevel = "LOW";

        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);

        result.put("reorderPoint", Math.round(reorderPoint));
        result.put("safetyStock", Math.round(safetyStock));
        result.put("reorderQty", Math.round(reorderQty));
        result.put("riskLevel", riskLevel);
        result.put("prediction", riskLevel);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictAnomaly(Map<String, Object> input, Map<String, Object> result) {
        double orderValue = input.containsKey("orderValue") ? ((Number) input.get("orderValue")).doubleValue() : 0.0;
        int itemCount = input.containsKey("itemCount") ? ((Number) input.get("itemCount")).intValue() : 1;
        boolean shippingAddressChanged = input.containsKey("shippingAddressChanged") && Boolean.parseBoolean(input.get("shippingAddressChanged").toString());
        boolean newCustomer = input.containsKey("newCustomer") && Boolean.parseBoolean(input.get("newCustomer").toString());
        boolean paymentMethodChanged = input.containsKey("paymentMethodChanged") && Boolean.parseBoolean(input.get("paymentMethodChanged").toString());

        double valueScore = Math.min(orderValue / 1000.0, 0.4);
        double itemScore = itemCount > 10 ? 0.3 : itemCount > 5 ? 0.2 : 0.05;
        double addressScore = shippingAddressChanged ? 0.15 : 0.0;
        double newCustomerScore = newCustomer ? 0.25 : 0.0;
        double paymentScore = paymentMethodChanged ? 0.2 : 0.0;

        double anomalyScore = valueScore + itemScore + addressScore + newCustomerScore + paymentScore;
        boolean isAnomaly = anomalyScore > 0.7;

        List<String> flaggedReasons = new ArrayList<>();
        if (valueScore > 0.25) flaggedReasons.add("HIGH_ORDER_VALUE");
        if (itemScore > 0.2) flaggedReasons.add("HIGH_ITEM_COUNT");
        if (shippingAddressChanged) flaggedReasons.add("SHIPPING_ADDRESS_CHANGED");
        if (newCustomer) flaggedReasons.add("NEW_CUSTOMER");
        if (paymentMethodChanged) flaggedReasons.add("PAYMENT_METHOD_CHANGED");

        String severity = !isAnomaly ? "NONE" : anomalyScore > 0.9 ? "CRITICAL" : anomalyScore > 0.8 ? "HIGH" : "MEDIUM";

        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.1, 0.99);

        result.put("isAnomaly", isAnomaly);
        result.put("anomalyScore", Math.round(anomalyScore * 100.0) / 100.0);
        result.put("severity", severity);
        result.put("flaggedReasons", flaggedReasons);
        result.put("prediction", isAnomaly ? "ANOMALY_DETECTED" : "NORMAL");
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictAssistant(Map<String, Object> input, Map<String, Object> result) {
        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);
        result.put("response", "Based on your data, I recommend reviewing inventory levels for top-selling SKUs in the electronics category. Would you like me to generate a detailed report?");
        result.put("suggestedActions", List.of("View Inventory Report", "Check Low Stock Items", "Review Demand Forecast"));
        result.put("prediction", "Analysis complete");
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    private void predictDocumentAi(Map<String, Object> input, Map<String, Object> result) {
        long providedFields = input.keySet().stream().filter(k -> input.get(k) != null).count();
        double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);
        result.put("extractedFields", Map.of(
                "invoiceNumber", "INV-2024-001",
                "totalAmount", 1250.00,
                "vendorName", "ABC Supply Co."));
        result.put("documentClass", "INVOICE");
        result.put("prediction", "INVOICE");
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("featuresUsed", input.keySet().toArray(new String[0]));
    }

    // ============================================================
    // Helper methods
    // ============================================================

    private Object deserializeJsonNode(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(e -> list.add(deserializeJsonNode(e)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), deserializeJsonNode(e.getValue())));
            return map;
        }
        return node.asText();
    }

    private String getRegionFromZip(String zip) {
        String normalized = zip.replaceAll("[^0-9]", "");
        if (normalized.isEmpty()) return "UNKNOWN";
        int prefix = Integer.parseInt(normalized.substring(0, Math.min(1, normalized.length())));
        if (prefix <= 2) return "NORTHEAST";
        if (prefix <= 4) return "SOUTHEAST";
        if (prefix <= 6) return "MIDWEST";
        if (prefix <= 7) return "SOUTH";
        if (prefix <= 8) return "MOUNTAIN";
        return "WEST";
    }

    private int getZoneDistance(String originRegion, String destRegion) {
        Map<String, Integer> regionIndex = Map.of(
                "NORTHEAST", 0, "SOUTHEAST", 1, "MIDWEST", 2,
                "SOUTH", 3, "MOUNTAIN", 4, "WEST", 5, "UNKNOWN", 6);
        int o = regionIndex.getOrDefault(originRegion, 6);
        int d = regionIndex.getOrDefault(destRegion, 6);
        return Math.abs(o - d);
    }

    private String getNearestWarehouse(String originRegion, String destRegion) {
        if (destRegion.equals("UNKNOWN")) return "wh-1";
        int zoneDist = getZoneDistance(originRegion, destRegion);
        if (zoneDist <= 1) return "wh-1";
        if (zoneDist <= 2) return "wh-2";
        return "wh-3";
    }

    private String getWarehouseName(String warehouseId) {
        return switch (warehouseId) {
            case "wh-1" -> "Regional Hub Alpha";
            case "wh-2" -> "Regional Hub Beta";
            case "wh-3" -> "National Distribution Center";
            default -> "Fulfillment Center";
        };
    }
}
