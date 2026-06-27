package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private final AiGatewayRouteRepository gatewayRouteRepository;
    private final AiDeploymentRepository deploymentRepository;
    private final AiModelRepository modelRepository;
    private final AiModelVersionRepository versionRepository;
    private final AiInferenceLogRepository inferenceLogRepository;
    private final AiRuleEngineService ruleEngineService;
    private final AiInferenceService inferenceService;
    private final ObjectMapper objectMapper;

    public AiGatewayService(AiGatewayRouteRepository gatewayRouteRepository,
                            AiDeploymentRepository deploymentRepository,
                            AiModelRepository modelRepository,
                            AiModelVersionRepository versionRepository,
                            AiInferenceLogRepository inferenceLogRepository,
                            AiRuleEngineService ruleEngineService,
                            AiInferenceService inferenceService,
                            ObjectMapper objectMapper) {
        this.gatewayRouteRepository = gatewayRouteRepository;
        this.deploymentRepository = deploymentRepository;
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.inferenceLogRepository = inferenceLogRepository;
        this.ruleEngineService = ruleEngineService;
        this.inferenceService = inferenceService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> predict(String modelType, Map<String, Object> input) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        AiGatewayRoute route = gatewayRouteRepository.findByTenantIdAndModelType(tenantId, modelType)
                .orElseGet(() -> gatewayRouteRepository.findByModelType(modelType)
                        .orElse(null));

        if (route == null) {
            log.warn("No gateway route found for modelType={}, tenant={}. Using rule engine fallback.", modelType, tenantId);
            return executeFallback(tenantId, modelType, input, "NO_ROUTE", startTime);
        }

        AiDeployment deployment = deploymentRepository
                .findByTenantIdAndModelIdAndEnvironment(tenantId, findModelIdByType(tenantId, modelType), "PRODUCTION")
                .orElse(null);

        if (deployment == null || !"ACTIVE".equals(deployment.getStatus())) {
            log.warn("No active deployment for modelType={}, tenant={}. Using fallback.", modelType, tenantId);
            return executeFallback(tenantId, modelType, input, "NO_DEPLOYMENT", startTime);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > route.getTimeoutMs()) {
            log.warn("Gateway timeout for modelType={}. Using fallback.", modelType);
            return executeFallback(tenantId, modelType, input, "TIMEOUT", startTime);
        }

        try {
            Map<String, Object> prediction = inferenceService.execute(
                    deployment.getModelId(), deployment.getVersionId(), input);

            inferenceLogRepository.save(AiInferenceLog.builder()
                    .tenantId(tenantId)
                    .modelId(deployment.getModelId())
                    .versionId(deployment.getVersionId())
                    .deploymentId(deployment.getId())
                    .requestId(UUID.randomUUID().toString())
                    .inputData(toJson(input))
                    .outputData(toJson(prediction))
                    .confidence(extractConfidence(prediction))
                    .latencyMs(BigDecimal.valueOf(System.currentTimeMillis() - startTime))
                    .status("SUCCESS")
                    .fallbackUsed(false)
                    .ruleEngineUsed(false)
                    .sourceService("ai_gateway")
                    .createdAt(LocalDateTime.now())
                    .build());

            BigDecimal confidence = extractConfidence(prediction);
            if (route.getFallbackStrategy() != null && confidence != null && confidence.compareTo(new BigDecimal("0.80")) < 0) {
                log.info("Low confidence ({}) for modelType={}. Using fallback.", confidence, modelType);
                Map<String, Object> fallbackResult = executeFallback(tenantId, modelType, input, "LOW_CONFIDENCE", startTime);
                prediction.put("aiPrediction", prediction);
                prediction.put("appliedPrediction", fallbackResult);
                prediction.put("fallbackReason", "LOW_CONFIDENCE");
                return prediction;
            }

            return prediction;
        } catch (Exception e) {
            log.error("AI prediction failed for modelType={}: {}", modelType, e.getMessage());
            return executeFallback(tenantId, modelType, input, "PREDICTION_ERROR", startTime);
        }
    }

    private Map<String, Object> executeFallback(UUID tenantId, String modelType, Map<String, Object> input,
                                                 String reason, long startTime) {
        Map<String, Object> result = ruleEngineService.executeFallback(tenantId, modelType, input);
        logInferenceFallback(tenantId, modelType, input, result, reason, startTime);
        return result;
    }

    private void logInferenceFallback(UUID tenantId, String modelType, Map<String, Object> input,
                                       Map<String, Object> output, String reason, long startTime) {
        try {
            UUID modelId = findModelIdByType(tenantId, modelType);
            inferenceLogRepository.save(AiInferenceLog.builder()
                    .tenantId(tenantId)
                    .modelId(modelId)
                    .requestId(UUID.randomUUID().toString())
                    .inputData(toJson(input))
                    .outputData(toJson(output))
                    .latencyMs(BigDecimal.valueOf(System.currentTimeMillis() - startTime))
                    .status("FALLBACK")
                    .fallbackUsed(true)
                    .fallbackReason(reason)
                    .ruleEngineUsed(true)
                    .sourceService("ai_gateway")
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to log inference fallback: {}", e.getMessage());
        }
    }

    private UUID findModelIdByType(UUID tenantId, String modelType) {
        return modelRepository.findAvailableForTenant(tenantId, modelType, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().map(AiModel::getId).orElse(null);
    }

    private BigDecimal extractConfidence(Map<String, Object> prediction) {
        if (prediction == null) return null;
        Object conf = prediction.get("confidence");
        if (conf instanceof Number) return BigDecimal.valueOf(((Number) conf).doubleValue());
        return null;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
