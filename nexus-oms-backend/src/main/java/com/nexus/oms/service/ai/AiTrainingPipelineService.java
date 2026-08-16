package com.nexus.oms.service.ai;

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

@Service
public class AiTrainingPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AiTrainingPipelineService.class);

    private final AiTrainingJobRepository trainingJobRepository;
    private final AiModelRepository modelRepository;
    private final AiModelVersionRepository versionRepository;

    public AiTrainingPipelineService(AiTrainingJobRepository trainingJobRepository,
                                      AiModelRepository modelRepository,
                                      AiModelVersionRepository versionRepository) {
        this.trainingJobRepository = trainingJobRepository;
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
    }

    public Page<AiTrainingJob> getJobs(UUID tenantId, UUID modelId, String status, Pageable pageable) {
        if (modelId != null) return trainingJobRepository.findByModelId(modelId, pageable);
        if (status != null) return trainingJobRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        return trainingJobRepository.findByTenantId(tenantId, pageable);
    }

    public Optional<AiTrainingJob> getJob(UUID jobId) {
        return trainingJobRepository.findById(jobId);
    }

    @Transactional
    public AiTrainingJob createJob(UUID tenantId, UUID modelId, Map<String, Object> config) {
        AiTrainingJob job = AiTrainingJob.builder()
                .tenantId(tenantId)
                .modelId(modelId)
                .name((String) config.getOrDefault("name", "Training Job"))
                .jobType("MANUAL")
                .status("PENDING")
                .config(toJson(config))
                .hyperparameters(toJson(config.getOrDefault("hyperparameters", Map.of())))
                .createdBy(TenantContext.getCurrentUsername())
                .build();
        return trainingJobRepository.save(job);
    }

    @Transactional
    public AiTrainingJob startJob(UUID jobId) {
        AiTrainingJob job = trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());

        AiModel model = modelRepository.findById(job.getModelId()).orElse(null);
        if (model != null) {
            model.setStatus("TRAINING");
            modelRepository.save(model);
        }

        log.info("Training job {} started for model {}", jobId, job.getModelId());
        return trainingJobRepository.save(job);
    }

    @Transactional
    public AiTrainingJob completeJob(UUID jobId, Map<String, Object> results) {
        AiTrainingJob job = trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        job.setStatus("COMPLETED");
        job.setCompletedAt(LocalDateTime.now());

        boolean hasRealMetrics = hasMetric(results, "accuracy") || hasMetric(results, "precision")
                || hasMetric(results, "recall") || hasMetric(results, "f1Score") || hasMetric(results, "loss")
                || hasMetric(results, "mae") || hasMetric(results, "rmse")
                || hasMetric(results, "wape") || hasMetric(results, "pinball");

        if (hasRealMetrics) {
            job.setAccuracy(toDecimal(results.get("accuracy")));
            job.setPrecision(toDecimal(results.get("precision")));
            job.setRecall(toDecimal(results.get("recall")));
            job.setF1Score(toDecimal(results.get("f1Score")));
            job.setLoss(toDecimal(results.get("loss")));
            job.setDriftScore(toDecimal(results.get("driftScore")));
            job.setMetricsSource("REAL");
        } else {
            job.setAccuracy(null);
            job.setPrecision(null);
            job.setRecall(null);
            job.setF1Score(null);
            job.setLoss(null);
            job.setDriftScore(null);
            job.setMetricsSource("NO_METRICS");
            log.warn("Training job {} completed without real evaluation metrics; marked NO_METRICS", jobId);
        }

        job.setEpochs(toInt(results.get("epochs")));
        job.setDatasetSize(toInt(results.get("datasetSize")));
        job.setDurationSeconds(toInt(results.get("durationSeconds")));

        AiTrainingJob saved = trainingJobRepository.save(job);

        AiModel model = modelRepository.findById(job.getModelId()).orElse(null);
        if (model != null) {
            model.setStatus("ACTIVE");

            if (hasRealMetrics) {
                AiModelVersion version = AiModelVersion.builder()
                        .modelId(model.getId())
                        .version("v" + (versionRepository.countByModelId(model.getId()) + 1) + ".0.0")
                        .accuracy(job.getAccuracy())
                        .precision(job.getPrecision())
                        .recall(job.getRecall())
                        .f1Score(job.getF1Score())
                        .status("VALIDATING")
                        .trainingJobId(jobId)
                        .createdBy(TenantContext.getCurrentUsername())
                        .build();

                if (hasMetric(results, "mae") || hasMetric(results, "rmse")
                        || hasMetric(results, "wape") || hasMetric(results, "pinball")) {
                    Map<String, Object> forecast = new LinkedHashMap<>();
                    forecast.put("mae", results.get("mae"));
                    forecast.put("rmse", results.get("rmse"));
                    forecast.put("wape", results.get("wape"));
                    forecast.put("pinball", results.get("pinball"));
                    version.setMetrics(toJson(forecast));
                }

                versionRepository.save(version);

                model.setCurrentVersion(version.getVersion());
                log.info("Training job {} completed. New version {} created.", jobId, version.getVersion());
            } else {
                log.warn("Training job {} completed without metrics; no model version recorded.", jobId);
            }
            modelRepository.save(model);
        }

        return saved;
    }

    private boolean hasMetric(Map<String, Object> results, String key) {
        return results.containsKey(key) && results.get(key) != null;
    }

    private BigDecimal toDecimal(Object value) {
        return value instanceof Number ? BigDecimal.valueOf(((Number) value).doubleValue()) : null;
    }

    private Integer toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    @Transactional
    public AiTrainingJob failJob(UUID jobId, String errorMessage) {
        AiTrainingJob job = trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        job.setStatus("FAILED");
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(errorMessage);
        AiTrainingJob saved = trainingJobRepository.save(job);

        AiModel model = modelRepository.findById(job.getModelId()).orElse(null);
        if (model != null && "TRAINING".equals(model.getStatus())) {
            model.setStatus("ACTIVE");
            modelRepository.save(model);
        }
        return saved;
    }

    @Transactional
    public void scheduleRetraining(UUID modelId) {
        AiTrainingJob job = AiTrainingJob.builder()
                .modelId(modelId)
                .jobType("SCHEDULED")
                .status("PENDING")
                .triggerReason("Scheduled weekly retraining")
                .config("{\"type\":\"scheduled\",\"schedule\":\"weekly\"}")
                .createdBy("system")
                .build();
        trainingJobRepository.save(job);
        log.info("Scheduled retraining for model {}", modelId);
    }

    private String toJson(Object obj) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
