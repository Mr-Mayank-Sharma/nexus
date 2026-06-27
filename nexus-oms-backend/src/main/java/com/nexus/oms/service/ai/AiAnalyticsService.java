package com.nexus.oms.service.ai;

import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiAnalyticsService {

    private final AiInferenceLogRepository inferenceLogRepository;
    private final AiTrainingJobRepository trainingJobRepository;
    private final AiCostLogRepository costLogRepository;
    private final AiModelRepository modelRepository;
    private final AiDeploymentRepository deploymentRepository;

    public AiAnalyticsService(AiInferenceLogRepository inferenceLogRepository,
                               AiTrainingJobRepository trainingJobRepository,
                               AiCostLogRepository costLogRepository,
                               AiModelRepository modelRepository,
                               AiDeploymentRepository deploymentRepository) {
        this.inferenceLogRepository = inferenceLogRepository;
        this.trainingJobRepository = trainingJobRepository;
        this.costLogRepository = costLogRepository;
        this.modelRepository = modelRepository;
        this.deploymentRepository = deploymentRepository;
    }

    public Map<String, Object> getTenantDashboard(UUID tenantId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime thisWeek = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime thisMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        dashboard.put("predictionsToday", inferenceLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, today));
        dashboard.put("predictionsThisWeek", inferenceLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, thisWeek));
        dashboard.put("fallbacksToday", inferenceLogRepository.countByTenantIdAndFallbackUsed(tenantId, true));
        dashboard.put("ruleEngineUsedToday", inferenceLogRepository.countByTenantIdAndRuleEngineUsed(tenantId, true));

        BigDecimal totalCost = costLogRepository.sumByTenantAndTypeSince(tenantId, "INFERENCE", thisMonth);
        BigDecimal trainingCost = costLogRepository.sumByTenantAndTypeSince(tenantId, "TRAINING", thisMonth);
        BigDecimal storageCost = costLogRepository.sumByTenantAndTypeSince(tenantId, "STORAGE", thisMonth);
        BigDecimal computeCost = costLogRepository.sumByTenantAndTypeSince(tenantId, "COMPUTE", thisMonth);

        Map<String, BigDecimal> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("inference", totalCost != null ? totalCost : BigDecimal.ZERO);
        costBreakdown.put("training", trainingCost != null ? trainingCost : BigDecimal.ZERO);
        costBreakdown.put("storage", storageCost != null ? storageCost : BigDecimal.ZERO);
        costBreakdown.put("compute", computeCost != null ? computeCost : BigDecimal.ZERO);
        dashboard.put("costsThisMonth", costBreakdown);

        List<Map<String, Object>> modelPerformance = new ArrayList<>();
        modelRepository.findByTenantId(tenantId, org.springframework.data.domain.PageRequest.of(0, 100))
                .forEach(m -> {
                    Map<String, Object> perf = new LinkedHashMap<>();
                    perf.put("id", m.getId());
                    perf.put("name", m.getDisplayName() != null ? m.getDisplayName() : m.getName());
                    perf.put("modelType", m.getModelType());
                    perf.put("status", m.getStatus());
                    perf.put("currentVersion", m.getCurrentVersion());
                    perf.put("predictionsToday", inferenceLogRepository
                            .countByTenantIdAndModelIdAndStatus(tenantId, m.getId(), "SUCCESS"));
                    BigDecimal avgLatency = inferenceLogRepository
                            .avgLatencyByModelSince(m.getId(), today);
                    perf.put("avgLatencyMs", avgLatency);
                    BigDecimal acc = trainingJobRepository.avgAccuracyByModel(m.getId());
                    perf.put("accuracy", acc);
                    modelPerformance.add(perf);
                });
        dashboard.put("modelPerformance", modelPerformance);

        List<Map<String, Object>> deployments = new ArrayList<>();
        deploymentRepository.findByTenantId(tenantId)
                .forEach(d -> {
                    Map<String, Object> dep = new LinkedHashMap<>();
                    dep.put("id", d.getId());
                    dep.put("environment", d.getEnvironment());
                    dep.put("status", d.getStatus());
                    dep.put("trafficWeight", d.getTrafficWeight());
                    modelRepository.findById(d.getModelId()).ifPresent(m ->
                            dep.put("modelName", m.getDisplayName() != null ? m.getDisplayName() : m.getName()));
                    deployments.add(dep);
                });
        dashboard.put("activeDeployments", deployments.size());
        dashboard.put("deployments", deployments);

        return dashboard;
    }
}
