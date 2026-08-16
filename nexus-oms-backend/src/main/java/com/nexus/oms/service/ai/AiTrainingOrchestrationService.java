package com.nexus.oms.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.AiModel;
import com.nexus.oms.entity.ai.AiModelVersion;
import com.nexus.oms.entity.ai.AiTrainingJob;
import com.nexus.oms.repository.ai.AiModelRepository;
import com.nexus.oms.repository.ai.AiModelVersionRepository;
import com.nexus.oms.repository.ai.AiTrainingJobRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AiTrainingOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiTrainingOrchestrationService.class);

    public static final String DEMAND_FORECAST_TYPE = "DEMAND_FORECAST";
    public static final int LOOKBACK_DAYS = 90;

    private final AiTrainingDataService trainingDataService;
    private final AiTrainingPipelineService pipelineService;
    private final AiArtifactService artifactService;
    private final AiModelRepository modelRepository;
    private final AiModelVersionRepository versionRepository;
    private final AiTrainingJobRepository jobRepository;
    private final TrainingScriptExecutor scriptExecutor;
    private final ObjectMapper objectMapper;
    private final Path scriptPath;

    public AiTrainingOrchestrationService(AiTrainingDataService trainingDataService,
                                          AiTrainingPipelineService pipelineService,
                                          AiArtifactService artifactService,
                                          AiModelRepository modelRepository,
                                          AiModelVersionRepository versionRepository,
                                          AiTrainingJobRepository jobRepository,
                                          TrainingScriptExecutor scriptExecutor,
                                          ObjectMapper objectMapper,
                                          @Value("${nexus.ai.trainer-script:../scripts/ml/forecast.py}") String scriptPath) {
        this.trainingDataService = trainingDataService;
        this.pipelineService = pipelineService;
        this.artifactService = artifactService;
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.jobRepository = jobRepository;
        this.scriptExecutor = scriptExecutor;
        this.objectMapper = objectMapper;
        this.scriptPath = Path.of(scriptPath).toAbsolutePath().normalize();
    }

    /**
     * Run a training job against the real Python trainer using real exported demand
     * data. Fails honestly when there is no demand history or the trainer is unavailable.
     */
    @Transactional
    public AiTrainingJob runJob(UUID jobId) {
        AiTrainingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        UUID tenantId = job.getTenantId() != null ? job.getTenantId() : TenantContext.getCurrentTenantId();

        long demandRecords = trainingDataService.getDemandRecordCount(tenantId, LOOKBACK_DAYS);
        if (demandRecords == 0) {
            pipelineService.failJob(jobId, "No demand history available to train on (tenant " + tenantId + ")");
            return jobRepository.findById(jobId).orElseThrow();
        }

        pipelineService.startJob(jobId);
        long startedAt = System.currentTimeMillis();
        try {
            String jsonl = trainingDataService.exportDemandJsonl(tenantId, LOOKBACK_DAYS);

            Path tempDir = Files.createTempDirectory("nexus-train");
            Path input = tempDir.resolve("demand.jsonl");
            Files.writeString(input, jsonl);
            Path outputDir = tempDir.resolve("artifacts");

            if (!Files.exists(scriptPath)) {
                pipelineService.failJob(jobId, "Training script not found at " + scriptPath);
                return jobRepository.findById(jobId).orElseThrow();
            }

            TrainingScriptExecutor.TrainingScriptResult result = scriptExecutor.execute(scriptPath, input, outputDir);
            if (result.exitCode() != 0) {
                pipelineService.failJob(jobId, "Trainer exited with code " + result.exitCode() + ": " + truncate(result.output(), 2000));
                return jobRepository.findById(jobId).orElseThrow();
            }

            Map<String, Object> metrics = parseMetrics(outputDir);
            int durationSeconds = (int) ((System.currentTimeMillis() - startedAt) / 1000);

            Map<String, Object> results = new HashMap<>();
            results.put("mae", metrics.get("mae"));
            results.put("rmse", metrics.get("rmse"));
            results.put("wape", metrics.get("wape"));
            results.put("pinball", metrics.get("pinball"));
            results.put("datasetSize", demandRecords);
            results.put("durationSeconds", durationSeconds);
            results.put("epochs", 1);

            pipelineService.completeJob(jobId, results);
            attachArtifacts(jobId, outputDir);

            log.info("Training job {} completed with real metrics {} after {}s", jobId, metrics, durationSeconds);
            return jobRepository.findById(jobId).orElseThrow();
        } catch (IOException e) {
            log.error("Training job {} failed to run trainer", jobId, e);
            pipelineService.failJob(jobId, "Trainer I/O error: " + e.getMessage());
            return jobRepository.findById(jobId).orElseThrow();
        } catch (Exception e) {
            log.error("Training job {} failed", jobId, e);
            pipelineService.failJob(jobId, e.getMessage());
            return jobRepository.findById(jobId).orElseThrow();
        }
    }

    @SuppressWarnings("unchecked")
    private void attachArtifacts(UUID jobId, Path outputDir) throws IOException {
        AiTrainingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        AiModelVersion version = versionRepository.findTopByTrainingJobIdOrderByCreatedAtDesc(jobId).orElse(null);
        if (version == null) {
            log.warn("No model version recorded for job {}; skipping artifact attachment", jobId);
            return;
        }

        Path modelOnnx = outputDir.resolve("model.onnx");
        if (Files.exists(modelOnnx)) {
            artifactService.storeArtifact(version.getModelId(), version.getId(), Files.readAllBytes(modelOnnx), "model.onnx");
        }

        List<String> featureColumns = readFeatureColumns(outputDir);
        Map<String, Double> skuRatios = readCalibrationRatios(outputDir);
        artifactService.attachMetadata(job.getTenantId(), version.getModelId(), version.getId(),
                featureColumns, skuRatios, "GLOBAL_RATIO");
    }

    private Map<String, Object> parseMetrics(Path outputDir) throws IOException {
        Path metricsFile = outputDir.resolve("metrics.json");
        if (!Files.exists(metricsFile)) {
            throw new IOException("metrics.json not produced by trainer");
        }
        JsonNode node = objectMapper.readTree(Files.readAllBytes(metricsFile));
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("mae", numberOrNull(node, "mae"));
        metrics.put("rmse", numberOrNull(node, "rmse"));
        metrics.put("wape", numberOrNull(node, "wape"));
        metrics.put("pinball", numberOrNull(node, "pinball"));
        return metrics;
    }

    private List<String> readFeatureColumns(Path outputDir) {
        try {
            Path file = outputDir.resolve("feature_columns.json");
            if (!Files.exists(file)) {
                return AiTrainingDataService.FEATURE_COLUMNS;
            }
            return objectMapper.readValue(Files.readAllBytes(file), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to read feature_columns.json: {}", e.getMessage());
            return AiTrainingDataService.FEATURE_COLUMNS;
        }
    }

    private Map<String, Double> readCalibrationRatios(Path outputDir) {
        try {
            Path file = outputDir.resolve("calibration.json");
            if (!Files.exists(file)) {
                return Map.of();
            }
            JsonNode node = objectMapper.readTree(Files.readAllBytes(file));
            Map<String, Double> ratios = new HashMap<>();
            node.fields().forEachRemaining(e -> ratios.put(e.getKey(), e.getValue().asDouble(1.0)));
            return ratios;
        } catch (Exception e) {
            log.warn("Failed to read calibration.json: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Weekly retraining: create a pending job for each active demand-forecast model. */
    @Scheduled(cron = "0 0 3 * * MON")
    public void scheduleWeeklyRetraining() {
        try {
            List<AiModel> demandModels = modelRepository.findAvailableForTenant(
                            UUID.fromString("00000000-0000-0000-0000-000000000000"),
                            DEMAND_FORECAST_TYPE, PageRequest.of(0, 100))
                    .getContent();
            for (AiModel model : demandModels) {
                if (!"ACTIVE".equalsIgnoreCase(model.getStatus())) {
                    continue;
                }
                pipelineService.scheduleRetraining(model.getId());
                log.info("Scheduled weekly retraining for model {}", model.getId());
            }
        } catch (Exception e) {
            log.error("Weekly retraining scheduling failed", e);
        }
    }

    private Object numberOrNull(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
