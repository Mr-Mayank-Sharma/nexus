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
    private final Random random = new Random();

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

    private Map<String, Object> generatePrediction(AiModel model, AiModelVersion version, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelName", model.getName());
        result.put("modelVersion", version != null ? version.getVersion() : "unknown");
        result.put("timestamp", LocalDateTime.now().toString());

        String modelType = model.getModelType();
        switch (modelType) {
            case "DEMAND_FORECAST":
                result.put("predictedOrders", 100 + random.nextInt(900));
                result.put("confidence", 0.85 + random.nextDouble() * 0.14);
                result.put("lowerBound", 80);
                result.put("upperBound", 120);
                result.put("unit", "units");
                break;
            case "SMART_ALLOCATOR":
                result.put("warehouseId", "wh-" + (1 + random.nextInt(5)));
                result.put("warehouseName", "Warehouse " + (char)('A' + random.nextInt(5)));
                result.put("shippingCost", 5.99 + random.nextDouble() * 20);
                result.put("estimatedDays", 1 + random.nextInt(5));
                result.put("confidence", 0.80 + random.nextDouble() * 0.19);
                break;
            case "CARRIER_OPTIMIZER":
                String[] carriers = {"FEDEX", "UPS", "DHL", "USPS"};
                String[] services = {"GROUND", "2_DAY", "OVERNIGHT", "EXPRESS"};
                result.put("carrier", carriers[random.nextInt(carriers.length)]);
                result.put("serviceLevel", services[random.nextInt(services.length)]);
                result.put("cost", 4.99 + random.nextDouble() * 30);
                result.put("estimatedDelivery", LocalDate.now().plusDays(1 + random.nextInt(5)).toString());
                result.put("confidence", 0.75 + random.nextDouble() * 0.24);
                break;
            case "RETURNS_PREDICTOR":
                result.put("returnProbability", random.nextDouble() * 0.4);
                result.put("expectedReturnDate", LocalDate.now().plusDays(7 + random.nextInt(23)).toString());
                result.put("confidence", 0.70 + random.nextDouble() * 0.25);
                result.put("topReasons", List.of("SIZE_ISSUE", "QUALITY_CONCERN", "DAMAGED"));
                break;
            case "INVENTORY_OPTIMIZER":
                result.put("reorderPoint", 50 + random.nextInt(200));
                result.put("safetyStock", 20 + random.nextInt(80));
                result.put("reorderQty", 100 + random.nextInt(900));
                result.put("confidence", 0.80 + random.nextDouble() * 0.19);
                result.put("riskLevel", random.nextDouble() < 0.2 ? "HIGH" : "LOW");
                break;
            case "ANOMALY_DETECTOR":
                boolean isAnomaly = random.nextDouble() < 0.1;
                result.put("isAnomaly", isAnomaly);
                result.put("anomalyScore", isAnomaly ? 0.7 + random.nextDouble() * 0.3 : random.nextDouble() * 0.3);
                result.put("confidence", 0.80 + random.nextDouble() * 0.19);
                result.put("severity", isAnomaly ? "MEDIUM" : "NONE");
                break;
            case "AI_ASSISTANT":
                result.put("response", "Based on your data, I recommend reviewing inventory levels for top-selling SKUs in the electronics category. Would you like me to generate a detailed report?");
                result.put("suggestedActions", List.of("View Inventory Report", "Check Low Stock Items", "Review Demand Forecast"));
                result.put("confidence", 0.85);
                break;
            case "DOCUMENT_AI":
                result.put("extractedFields", Map.of("invoiceNumber", "INV-2024-001", "totalAmount", 1250.00, "vendorName", "ABC Supply Co."));
                result.put("confidence", 0.92);
                result.put("documentClass", "INVOICE");
                break;
            default:
                result.put("prediction", "unknown");
                result.put("confidence", 0.5);
        }
        return result;
    }

    private BigDecimal extractConfidence(Map<String, Object> prediction) {
        Object conf = prediction.get("confidence");
        if (conf instanceof Number) return BigDecimal.valueOf(((Number) conf).doubleValue());
        return null;
    }
}
