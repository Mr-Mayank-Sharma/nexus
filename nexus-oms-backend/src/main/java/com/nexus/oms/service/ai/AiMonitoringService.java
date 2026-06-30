package com.nexus.oms.service.ai;

import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(AiMonitoringService.class);

    private final AiModelRepository modelRepository;
    private final AiModelMetricRepository metricRepository;
    private final AiInferenceLogRepository inferenceLogRepository;
    private final AiTrainingJobRepository trainingJobRepository;
    private final AiCostLogRepository costLogRepository;

    public AiMonitoringService(AiModelRepository modelRepository,
                                AiModelMetricRepository metricRepository,
                                AiInferenceLogRepository inferenceLogRepository,
                                AiTrainingJobRepository trainingJobRepository,
                                AiCostLogRepository costLogRepository) {
        this.modelRepository = modelRepository;
        this.metricRepository = metricRepository;
        this.inferenceLogRepository = inferenceLogRepository;
        this.trainingJobRepository = trainingJobRepository;
        this.costLogRepository = costLogRepository;
    }

    public Map<String, Object> getModelHealth(UUID modelId) {
        Map<String, Object> health = new LinkedHashMap<>();
        AiModel model = modelRepository.findById(modelId).orElse(null);
        if (model == null) return Map.of("status", "NOT_FOUND");

        health.put("modelName", model.getName());
        health.put("displayName", model.getDisplayName());
        health.put("status", model.getStatus());
        health.put("currentVersion", model.getCurrentVersion());
        health.put("category", model.getCategory());
        health.put("modelType", model.getModelType());

        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
        long recentPredictions = inferenceLogRepository
                .countByModelIdAndStatusAndCreatedAtAfter(modelId, "SUCCESS", lastHour);
        long recentFailures = inferenceLogRepository
                .countByModelIdAndStatusAndCreatedAtAfter(modelId, "FAILED", lastHour);

        BigDecimal avgLatency = inferenceLogRepository.avgLatencyByModelSince(modelId, lastHour);
        BigDecimal avgAccuracy = trainingJobRepository.avgAccuracyByModel(modelId);

        health.put("predictionsLastHour", recentPredictions);
        health.put("failuresLastHour", recentFailures);
        health.put("avgLatencyMs", avgLatency);
        health.put("avgAccuracy", avgAccuracy);

        List<AiModelMetric> recentMetrics = metricRepository
                .findByModelIdOrderByRecordedAtDesc(modelId, org.springframework.data.domain.PageRequest.of(0, 20));
        health.put("recentMetrics", recentMetrics);

        List<AiTrainingJob> recentTraining = trainingJobRepository
                .findByModelId(modelId, org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
        health.put("recentTraining", recentTraining);

        return health;
    }

    public Map<String, Object> getDashboardSummary(UUID tenantId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime thisMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long activeModels = modelRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
        long modelsInTraining = modelRepository.countByTenantIdAndStatus(tenantId, "TRAINING");
        long modelsWithError = modelRepository.countByTenantIdAndStatus(tenantId, "ERROR");
        long predictionsToday = inferenceLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, today);
        long fallbacksToday = inferenceLogRepository.countByTenantIdAndFallbackUsed(tenantId, true);

        summary.put("activeModels", activeModels);
        summary.put("modelsInTraining", modelsInTraining);
        summary.put("modelsWithError", modelsWithError);
        summary.put("predictionsToday", predictionsToday);
        summary.put("fallbacksToday", fallbacksToday);

        BigDecimal totalCostThisMonth = inferenceLogRepository.totalCostByTenantSince(tenantId, thisMonth);
        summary.put("totalCostThisMonth", totalCostThisMonth);

        List<Map<String, Object>> modelStatusList = new ArrayList<>();
        modelRepository.findByTenantId(tenantId, org.springframework.data.domain.PageRequest.of(0, 100))
                .forEach(m -> {
                    Map<String, Object> ms = new LinkedHashMap<>();
                    ms.put("id", m.getId());
                    ms.put("name", m.getName());
                    ms.put("displayName", m.getDisplayName());
                    ms.put("status", m.getStatus());
                    ms.put("modelType", m.getModelType());
                    ms.put("category", m.getCategory());
                    ms.put("currentVersion", m.getCurrentVersion());
                    modelStatusList.add(ms);
                });
        summary.put("models", modelStatusList);

        return summary;
    }

    @Transactional
    public void recordMetric(UUID modelId, String metricName, BigDecimal value) {
        metricRepository.save(AiModelMetric.builder()
                .modelId(modelId)
                .metricName(metricName)
                .metricValue(value)
                .build());
    }

    public boolean detectDrift(UUID modelId) {
        LocalDateTime lastWeek = LocalDateTime.now().minusDays(7);
        List<Object[]> avgMetrics = metricRepository.avgMetricsSince(modelId, lastWeek);
        for (Object[] row : avgMetrics) {
            String name = (String) row[0];
            Number avg = (Number) row[1];
            if ("accuracy".equals(name) && avg.doubleValue() < 0.70) {
                log.warn("Accuracy drift detected for model {}: {}", modelId, avg);
                return true;
            }
        }
        return false;
    }
}
