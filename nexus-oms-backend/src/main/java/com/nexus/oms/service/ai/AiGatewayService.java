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
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;
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

        // Weighted selection among ACTIVE deployments for this (tenant, model, env).
        // The trafficWeight column (set by the ramp ladder) determines the
        // probability of each deployment receiving a request.
        UUID routeModelId = findModelIdByType(tenantId, modelType);
        List<AiDeployment> actives = deploymentRepository
                .findAllByTenantIdAndModelIdAndEnvironment(tenantId, routeModelId, "PRODUCTION")
                .stream()
                .filter(d -> "ACTIVE".equals(d.getStatus()))
                .collect(Collectors.toList());

        AiDeployment deployment = selectByWeight(actives);

        if (deployment == null) {
            log.warn("No active deployment for modelType={}, tenant={}. Using fallback.", modelType, tenantId);
            return executeFallback(tenantId, modelType, input, "NO_DEPLOYMENT", startTime);
        }

        // Enforce the route's timeout around the ACTUAL inference call.
        // (Previously the check ran before inference started, so it only ever
        // measured the route/deployment lookups and never bounded the model.)
        long timeoutMs = route.getTimeoutMs() != null && route.getTimeoutMs() > 0
                ? route.getTimeoutMs() : 3000L;

        try {
            Map<String, Object> prediction = CompletableFuture
                    .supplyAsync(() -> {
                        // Inference runs off the request thread — propagate tenant context.
                        TenantContext.setCurrentTenantId(tenantId);
                        try {
                            return inferenceService.execute(
                                    deployment.getModelId(), deployment.getVersionId(), input);
                        } finally {
                            TenantContext.clear();
                        }
                    })
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();

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
                // Merged response — must NOT put a map into itself (stack overflow on serialize).
                Map<String, Object> merged = new LinkedHashMap<>();
                merged.put("aiPrediction", prediction);
                merged.put("appliedPrediction", fallbackResult);
                merged.put("fallbackReason", "LOW_CONFIDENCE");
                return merged;
            }

            return prediction;
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof TimeoutException) {
                log.warn("Inference timed out after {}ms for modelType={}. Using fallback.", timeoutMs, modelType);
                return executeFallback(tenantId, modelType, input, "TIMEOUT", startTime);
            }
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            log.error("AI prediction failed for modelType={}: {}", modelType, cause.getMessage());
            return executeFallback(tenantId, modelType, input, "PREDICTION_ERROR", startTime);
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

    /**
     * Weighted random selection among active deployments.
     * Falls back to the first entry if weights are zero/missing.
     */
    private AiDeployment selectByWeight(List<AiDeployment> deployments) {
        if (deployments.isEmpty()) return null;
        if (deployments.size() == 1) return deployments.get(0);

        BigDecimal total = deployments.stream()
                .map(d -> d.getTrafficWeight() != null ? d.getTrafficWeight() : BigDecimal.ONE)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) <= 0) return deployments.get(0);

        double roll = ThreadLocalRandom.current().nextDouble(total.doubleValue());
        double cumulative = 0;
        for (AiDeployment d : deployments) {
            cumulative += (d.getTrafficWeight() != null ? d.getTrafficWeight() : BigDecimal.ONE).doubleValue();
            if (roll < cumulative) return d;
        }
        return deployments.get(deployments.size() - 1);
    }
}
