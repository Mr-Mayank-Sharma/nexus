package com.nexus.oms.controller.ai;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ai.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiPlatformController {

    private final AiGatewayService gatewayService;
    private final AiModelRegistryService modelRegistryService;
    private final AiFeatureStoreService featureStoreService;
    private final AiTrainingPipelineService trainingPipelineService;
    private final AiInferenceService inferenceService;
    private final AiRuleEngineService ruleEngineService;
    private final AiMonitoringService monitoringService;
    private final AiAnalyticsService analyticsService;

    public AiPlatformController(AiGatewayService gatewayService,
                                 AiModelRegistryService modelRegistryService,
                                 AiFeatureStoreService featureStoreService,
                                 AiTrainingPipelineService trainingPipelineService,
                                 AiInferenceService inferenceService,
                                 AiRuleEngineService ruleEngineService,
                                 AiMonitoringService monitoringService,
                                 AiAnalyticsService analyticsService) {
        this.gatewayService = gatewayService;
        this.modelRegistryService = modelRegistryService;
        this.featureStoreService = featureStoreService;
        this.trainingPipelineService = trainingPipelineService;
        this.inferenceService = inferenceService;
        this.ruleEngineService = ruleEngineService;
        this.monitoringService = monitoringService;
        this.analyticsService = analyticsService;
    }

    private UUID tenant() { return TenantContext.getCurrentTenantId(); }

    // ========== GATEWAY ==========
    @PostMapping("/predict/{modelType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> predict(
            @PathVariable String modelType, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(ApiResponse.success(gatewayService.predict(modelType, input)));
    }

    // ========== MODEL REGISTRY ==========
    @GetMapping("/models")
    public ResponseEntity<ApiResponse<Page<AiModel>>> getModels(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                modelRegistryService.getModels(tenant(), category, status, PageRequest.of(page, size))));
    }

    @GetMapping("/models/{modelId}")
    public ResponseEntity<ApiResponse<AiModel>> getModel(@PathVariable UUID modelId) {
        return ResponseEntity.ok(ApiResponse.success(
                modelRegistryService.getModel(modelId).orElseThrow()));
    }

    @PostMapping("/models")
    public ResponseEntity<ApiResponse<AiModel>> createModel(@Valid @RequestBody AiModel model) {
        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.createModel(model)));
    }

    @PutMapping("/models/{modelId}")
    public ResponseEntity<ApiResponse<AiModel>> updateModel(
            @PathVariable UUID modelId, @RequestBody AiModel updates) {
        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.updateModel(modelId, updates)));
    }

    @GetMapping("/models/{modelId}/versions")
    public ResponseEntity<ApiResponse<List<AiModelVersion>>> getVersions(@PathVariable UUID modelId) {
        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.getVersions(modelId)));
    }

    @PostMapping("/models/{modelId}/versions")
    public ResponseEntity<ApiResponse<AiModelVersion>> createVersion(
            @PathVariable UUID modelId, @RequestBody AiModelVersion version) {
        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.createVersion(modelId, version)));
    }

    @PostMapping("/models/{modelId}/deploy/{versionId}")
    public ResponseEntity<ApiResponse<AiDeployment>> deploy(
            @PathVariable UUID modelId, @PathVariable UUID versionId,
            @RequestParam(defaultValue = "PRODUCTION") String environment) {
        return ResponseEntity.ok(ApiResponse.success(
                modelRegistryService.deploy(tenant(), modelId, versionId, environment)));
    }

    @PostMapping("/models/{modelId}/rollback/{versionId}")
    public ResponseEntity<ApiResponse<Void>> rollback(
            @PathVariable UUID modelId, @PathVariable UUID versionId) {
        modelRegistryService.rollback(tenant(), modelId, versionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Rolled back successfully"));
    }

    @GetMapping("/models/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getModelSummary() {
        return ResponseEntity.ok(ApiResponse.success(modelRegistryService.getRegistrySummary(tenant())));
    }

    // ========== FEATURE STORE ==========
    @GetMapping("/features")
    public ResponseEntity<ApiResponse<Page<AiFeatureDefinition>>> getFeatures(
            @RequestParam(required = false) String featureGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                featureStoreService.getDefinitions(tenant(), featureGroup, PageRequest.of(page, size))));
    }

    @PostMapping("/features")
    public ResponseEntity<ApiResponse<AiFeatureDefinition>> createFeature(
            @Valid @RequestBody AiFeatureDefinition def) {
        return ResponseEntity.ok(ApiResponse.success(featureStoreService.createDefinition(def)));
    }

    @GetMapping("/features/groups")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFeatureGroups() {
        return ResponseEntity.ok(ApiResponse.success(featureStoreService.getFeatureGroups(tenant())));
    }

    // ========== TRAINING PIPELINE ==========
    @GetMapping("/training/jobs")
    public ResponseEntity<ApiResponse<Page<AiTrainingJob>>> getTrainingJobs(
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                trainingPipelineService.getJobs(tenant(), modelId, status, PageRequest.of(page, size))));
    }

    @GetMapping("/training/jobs/{jobId}")
    public ResponseEntity<ApiResponse<AiTrainingJob>> getTrainingJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(trainingPipelineService.getJob(jobId).orElseThrow()));
    }

    @PostMapping("/training/jobs")
    public ResponseEntity<ApiResponse<AiTrainingJob>> createTrainingJob(
            @RequestParam UUID modelId, @RequestBody Map<String, Object> config) {
        return ResponseEntity.ok(ApiResponse.success(trainingPipelineService.createJob(tenant(), modelId, config)));
    }

    @PostMapping("/training/jobs/{jobId}/start")
    public ResponseEntity<ApiResponse<AiTrainingJob>> startTrainingJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(trainingPipelineService.startJob(jobId)));
    }

    @PostMapping("/training/jobs/{jobId}/complete")
    public ResponseEntity<ApiResponse<AiTrainingJob>> completeTrainingJob(
            @PathVariable UUID jobId, @RequestBody Map<String, Object> results) {
        return ResponseEntity.ok(ApiResponse.success(trainingPipelineService.completeJob(jobId, results)));
    }

    @PostMapping("/training/jobs/{jobId}/fail")
    public ResponseEntity<ApiResponse<AiTrainingJob>> failTrainingJob(
            @PathVariable UUID jobId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                trainingPipelineService.failJob(jobId, body.getOrDefault("error", "Unknown error"))));
    }

    // ========== MONITORING ==========
    @GetMapping("/monitoring/models/{modelId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getModelHealth(@PathVariable UUID modelId) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.getModelHealth(modelId)));
    }

    @GetMapping("/monitoring/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonitoringDashboard() {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.getDashboardSummary(tenant())));
    }

    // ========== ANALYTICS ==========
    @GetMapping("/analytics/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantDashboard() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getTenantDashboard(tenant())));
    }

    // ========== INFERENCE LOGS ==========
    @GetMapping("/models/{modelId}/inference-logs")
    public ResponseEntity<ApiResponse<Page<AiInferenceLog>>> getInferenceLogs(
            @PathVariable UUID modelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Collections.emptyList(), "Not implemented"));
    }

    // ========== RULE FALLBACKS ==========
    @GetMapping("/fallbacks/{modelId}")
    public ResponseEntity<ApiResponse<List<AiRuleFallback>>> getFallbacks(@PathVariable UUID modelId) {
        return ResponseEntity.ok(ApiResponse.success(ruleEngineService.executeFallback(
                tenant(), "DEMAND_FORECAST", Map.of()).entrySet().stream()
                .map(e -> AiRuleFallback.builder().name(e.getKey()).build())
                .toList()));
    }

    // ========== PREDICT (direct inference, no gateway) ==========
    @PostMapping("/predict/direct/{modelId}/{versionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> directPredict(
            @PathVariable UUID modelId, @PathVariable UUID versionId, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(ApiResponse.success(inferenceService.execute(modelId, versionId, input)));
    }
}
