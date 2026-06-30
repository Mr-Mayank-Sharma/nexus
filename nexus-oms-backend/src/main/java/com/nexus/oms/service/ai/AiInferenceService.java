package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import com.nexus.oms.security.TenantContext;
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
    private final ObjectMapper objectMapper;

    public AiInferenceService(AiModelRepository modelRepository,
                               AiModelVersionRepository versionRepository,
                               AiRuleFallbackRepository fallbackRepository,
                               AiInferenceLogRepository inferenceLogRepository,
                               ObjectMapper objectMapper) {
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.fallbackRepository = fallbackRepository;
        this.inferenceLogRepository = inferenceLogRepository;
        this.objectMapper = objectMapper;
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
        switch (modelType) {
            case "DEMAND_FORECAST": {
                double historicalAverage = input.containsKey("historicalAverage")
                        ? ((Number) input.get("historicalAverage")).doubleValue()
                        : 100.0;
                String seasonality = input.containsKey("seasonality")
                        ? input.get("seasonality").toString().toUpperCase()
                        : "MEDIUM";
                String trend = input.containsKey("trend")
                        ? input.get("trend").toString().toUpperCase()
                        : "STABLE";

                double seasonalityMultiplier;
                switch (seasonality) {
                    case "HIGH": seasonalityMultiplier = 1.3; break;
                    case "MEDIUM": seasonalityMultiplier = 1.15; break;
                    case "LOW": seasonalityMultiplier = 1.05; break;
                    default: seasonalityMultiplier = 1.0;
                }

                double trendFactor;
                switch (trend) {
                    case "UP": trendFactor = 1.1; break;
                    case "DOWN": trendFactor = 0.9; break;
                    default: trendFactor = 1.0;
                }

                double predictedOrders = historicalAverage * seasonalityMultiplier * trendFactor;
                double lowerBound = predictedOrders * 0.85;
                double upperBound = predictedOrders * 1.15;

                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.15, 0.99);

                result.put("predictedOrders", Math.round(predictedOrders * 100.0) / 100.0);
                result.put("lowerBound", Math.round(lowerBound * 100.0) / 100.0);
                result.put("upperBound", Math.round(upperBound * 100.0) / 100.0);
                result.put("unit", "units");
                result.put("prediction", Math.round(predictedOrders * 100.0) / 100.0);
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", String.format(
                        "Forecast based on historical avg %.0f, seasonality %s (x%.2f), trend %s (x%.2f)",
                        historicalAverage, seasonality, seasonalityMultiplier, trend, trendFactor));
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "SMART_ALLOCATOR": {
                String originZip = input.containsKey("originZip") ? input.get("originZip").toString() : "00000";
                String destZip = input.containsKey("destZip") ? input.get("destZip").toString() : "00000";
                double weightKg = input.containsKey("weightKg")
                        ? ((Number) input.get("weightKg")).doubleValue()
                        : 1.0;
                double declaredValue = input.containsKey("declaredValue")
                        ? ((Number) input.get("declaredValue")).doubleValue()
                        : 0.0;

                String originRegion = getRegionFromZip(originZip);
                String destRegion = getRegionFromZip(destZip);
                String warehouseId = getNearestWarehouse(originRegion, destRegion);
                String warehouseName = getWarehouseName(warehouseId);

                int zoneDistance = getZoneDistance(originRegion, destRegion);
                double baseRate = 3.50;
                double weightCost = weightKg * 0.45;
                double zoneCost = zoneDistance * 0.30;
                double valueSurcharge = declaredValue * 0.002;
                double shippingCost = baseRate + weightCost + zoneCost + valueSurcharge;

                int estimatedDays = 1 + zoneDistance;

                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);

                result.put("warehouseId", warehouseId);
                result.put("warehouseName", warehouseName);
                result.put("shippingCost", Math.round(shippingCost * 100.0) / 100.0);
                result.put("estimatedDays", estimatedDays);
                result.put("prediction", warehouseName);
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", String.format(
                        "Assigned %s for origin %s to destination %s (%d zones, %.1f kg, declared $%.2f)",
                        warehouseName, originRegion, destRegion, zoneDistance, weightKg, declaredValue));
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "CARRIER_OPTIMIZER": {
                double totalWeightKg = input.containsKey("totalWeightKg")
                        ? ((Number) input.get("totalWeightKg")).doubleValue()
                        : 1.0;
                double declaredValue = input.containsKey("declaredValue")
                        ? ((Number) input.get("declaredValue")).doubleValue()
                        : 0.0;
                String destination = input.containsKey("destination")
                        ? input.get("destination").toString()
                        : "UNKNOWN";

                String carrier;
                String serviceLevel;
                double baseCost;

                if (totalWeightKg < 2.0) {
                    carrier = "USPS";
                    serviceLevel = "GROUND";
                    baseCost = 3.99 + totalWeightKg * 0.80;
                } else if (totalWeightKg <= 10.0) {
                    carrier = "UPS";
                    serviceLevel = totalWeightKg > 5.0 ? "GROUND" : "2_DAY";
                    baseCost = 7.99 + totalWeightKg * 0.65;
                } else {
                    carrier = "FEDEX";
                    serviceLevel = "EXPRESS";
                    baseCost = 14.99 + totalWeightKg * 0.50;
                }

                double insuranceCost = declaredValue * 0.005;
                double cost = baseCost + insuranceCost;

                int deliveryDays;
                switch (serviceLevel) {
                    case "OVERNIGHT": deliveryDays = 1; break;
                    case "2_DAY": deliveryDays = 2; break;
                    case "EXPRESS": deliveryDays = 3; break;
                    default: deliveryDays = 5;
                }

                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.15, 0.99);

                result.put("carrier", carrier);
                result.put("serviceLevel", serviceLevel);
                result.put("cost", Math.round(cost * 100.0) / 100.0);
                result.put("estimatedDelivery", LocalDate.now().plusDays(deliveryDays).toString());
                result.put("prediction", carrier + " " + serviceLevel);
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", String.format(
                        "Selected %s %s for %.1f kg shipment to %s (estimated %d days, $%.2f)",
                        carrier, serviceLevel, totalWeightKg, destination, deliveryDays, cost));
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "RETURNS_PREDICTOR": {
                double customerReturnRate = input.containsKey("customerReturnRate")
                        ? ((Number) input.get("customerReturnRate")).doubleValue()
                        : 0.05;
                int orderHistoryMonths = input.containsKey("orderHistoryMonths")
                        ? ((Number) input.get("orderHistoryMonths")).intValue()
                        : 1;
                String productCategory = input.containsKey("productCategory")
                        ? input.get("productCategory").toString().toUpperCase()
                        : "GENERAL";
                double orderValue = input.containsKey("orderValue")
                        ? ((Number) input.get("orderValue")).doubleValue()
                        : 50.0;

                double categoryRisk;
                switch (productCategory) {
                    case "ELECTRONICS": categoryRisk = 1.4; break;
                    case "CLOTHING": categoryRisk = 1.6; break;
                    case "FOOD": categoryRisk = 1.1; break;
                    case "BOOKS": categoryRisk = 0.6; break;
                    case "FURNITURE": categoryRisk = 1.3; break;
                    default: categoryRisk = 1.0;
                }

                double valueFactor = Math.min(orderValue / 200.0, 1.5);
                double tenureFactor = Math.max(1.0 - (orderHistoryMonths - 1) * 0.02, 0.7);
                double returnProbability = Math.min(customerReturnRate * categoryRisk * valueFactor * tenureFactor, 0.5);

                List<String> topReasons;
                switch (productCategory) {
                    case "CLOTHING":
                        topReasons = List.of("SIZE_ISSUE", "STYLE_MISMATCH", "QUALITY_CONCERN");
                        break;
                    case "ELECTRONICS":
                        topReasons = List.of("DEFECTIVE", "NOT_AS_DESCRIBED", "COMPATIBILITY_ISSUE");
                        break;
                    case "FOOD":
                        topReasons = List.of("EXPIRY_CONCERN", "DAMAGED_PACKAGING", "QUALITY_CONCERN");
                        break;
                    case "FURNITURE":
                        topReasons = List.of("DAMAGED_IN_TRANSIT", "ASSEMBLY_ISSUE", "COLOR_MISMATCH");
                        break;
                    default:
                        topReasons = List.of("SIZE_ISSUE", "QUALITY_CONCERN", "DAMAGED");
                }

                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);

                int returnDays = 7 + (int) Math.round(orderHistoryMonths * 0.5);

                result.put("returnProbability", Math.round(returnProbability * 10000.0) / 10000.0);
                result.put("expectedReturnDate", LocalDate.now().plusDays(returnDays).toString());
                result.put("topReasons", topReasons);
                result.put("prediction", Math.round(returnProbability * 10000.0) / 10000.0);
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", String.format(
                        "Return probability %.1f%% for %s order ($%.2f) with historical rate %.1f%%",
                        returnProbability * 100, productCategory, orderValue, customerReturnRate * 100));
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "INVENTORY_OPTIMIZER": {
                double avgDailyDemand = input.containsKey("avgDailyDemand")
                        ? ((Number) input.get("avgDailyDemand")).doubleValue()
                        : 10.0;
                int leadTimeDays = input.containsKey("leadTimeDays")
                        ? ((Number) input.get("leadTimeDays")).intValue()
                        : 7;
                int currentStock = input.containsKey("currentStock")
                        ? ((Number) input.get("currentStock")).intValue()
                        : 100;
                int reorderFrequency = input.containsKey("reorderFrequency")
                        ? ((Number) input.get("reorderFrequency")).intValue()
                        : 14;

                double reorderPoint = avgDailyDemand * leadTimeDays * 1.3;
                double safetyStock = avgDailyDemand * leadTimeDays * 0.3;
                double reorderQty = avgDailyDemand * leadTimeDays * 2.0;

                double stockCoverageDays = currentStock / Math.max(avgDailyDemand, 0.1);
                String riskLevel;
                if (stockCoverageDays < leadTimeDays * 0.5) {
                    riskLevel = "CRITICAL";
                } else if (stockCoverageDays < leadTimeDays) {
                    riskLevel = "HIGH";
                } else if (stockCoverageDays < leadTimeDays * 1.5) {
                    riskLevel = "MEDIUM";
                } else {
                    riskLevel = "LOW";
                }

                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);

                result.put("reorderPoint", Math.round(reorderPoint));
                result.put("safetyStock", Math.round(safetyStock));
                result.put("reorderQty", Math.round(reorderQty));
                result.put("riskLevel", riskLevel);
                result.put("prediction", riskLevel);
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", String.format(
                        "Reorder at %d (safety %d), order %d units. Current stock %d covers %.1f days (risk: %s)",
                        Math.round(reorderPoint), Math.round(safetyStock), Math.round(reorderQty),
                        currentStock, stockCoverageDays, riskLevel));
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "ANOMALY_DETECTOR": {
                double orderValue = input.containsKey("orderValue")
                        ? ((Number) input.get("orderValue")).doubleValue()
                        : 0.0;
                int itemCount = input.containsKey("itemCount")
                        ? ((Number) input.get("itemCount")).intValue()
                        : 1;
                boolean shippingAddressChanged = input.containsKey("shippingAddressChanged")
                        && Boolean.parseBoolean(input.get("shippingAddressChanged").toString());
                boolean newCustomer = input.containsKey("newCustomer")
                        && Boolean.parseBoolean(input.get("newCustomer").toString());
                boolean paymentMethodChanged = input.containsKey("paymentMethodChanged")
                        && Boolean.parseBoolean(input.get("paymentMethodChanged").toString());

                double valueScore = Math.min(orderValue / 1000.0, 0.4);
                double itemScore = itemCount > 10 ? 0.3 : itemCount > 5 ? 0.2 : 0.05;
                double addressScore = shippingAddressChanged ? 0.15 : 0.0;
                double newCustomerScore = newCustomer ? 0.25 : 0.0;
                double paymentScore = paymentMethodChanged ? 0.2 : 0.0;

                double anomalyScore = valueScore + itemScore + addressScore + newCustomerScore + paymentScore;
                boolean isAnomaly = anomalyScore > 0.7;

                String severity;
                List<String> flaggedReasons = new ArrayList<>();
                if (valueScore > 0.25) flaggedReasons.add("HIGH_ORDER_VALUE");
                if (itemScore > 0.2) flaggedReasons.add("HIGH_ITEM_COUNT");
                if (shippingAddressChanged) flaggedReasons.add("SHIPPING_ADDRESS_CHANGED");
                if (newCustomer) flaggedReasons.add("NEW_CUSTOMER");
                if (paymentMethodChanged) flaggedReasons.add("PAYMENT_METHOD_CHANGED");

                if (isAnomaly) {
                    if (anomalyScore > 0.9) severity = "CRITICAL";
                    else if (anomalyScore > 0.8) severity = "HIGH";
                    else severity = "MEDIUM";
                } else {
                    severity = "NONE";
                }

                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.1, 0.99);

                result.put("isAnomaly", isAnomaly);
                result.put("anomalyScore", Math.round(anomalyScore * 100.0) / 100.0);
                result.put("severity", severity);
                result.put("flaggedReasons", flaggedReasons);
                result.put("prediction", isAnomaly ? "ANOMALY_DETECTED" : "NORMAL");
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", String.format(
                        "Anomaly score %.2f (%s). Flags: %s",
                        anomalyScore, severity, flaggedReasons.isEmpty() ? "none" : String.join(", ", flaggedReasons)));
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "AI_ASSISTANT": {
                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);
                result.put("response", "Based on your data, I recommend reviewing inventory levels for top-selling SKUs in the electronics category. Would you like me to generate a detailed report?");
                result.put("suggestedActions", List.of("View Inventory Report", "Check Low Stock Items", "Review Demand Forecast"));
                result.put("prediction", "Analysis complete");
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", "Standard AI assistant response for inventory management queries.");
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            case "DOCUMENT_AI": {
                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);
                result.put("extractedFields", Map.of(
                        "invoiceNumber", "INV-2024-001",
                        "totalAmount", 1250.00,
                        "vendorName", "ABC Supply Co."));
                result.put("documentClass", "INVOICE");
                result.put("prediction", "INVOICE");
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", "Document classified as INVOICE with extracted fields from standard template.");
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
                break;
            }
            default: {
                long providedFields = input.keySet().stream()
                        .filter(k -> input.get(k) != null)
                        .count();
                double confidence = Math.min(0.5 + providedFields * 0.12, 0.99);
                result.put("prediction", "unknown");
                result.put("confidence", Math.round(confidence * 100.0) / 100.0);
                result.put("explanation", "Unrecognized model type: " + modelType);
                result.put("featuresUsed", input.keySet().toArray(new String[0]));
            }
        }
        return result;
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
        switch (warehouseId) {
            case "wh-1": return "Regional Hub Alpha";
            case "wh-2": return "Regional Hub Beta";
            case "wh-3": return "National Distribution Center";
            default: return "Fulfillment Center";
        }
    }
}
