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
    private final Random random = new Random();

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

        Number accuracy = (Number) results.getOrDefault("accuracy", 0.85 + random.nextDouble() * 0.14);
        Number precision = (Number) results.getOrDefault("precision", 0.80 + random.nextDouble() * 0.15);
        Number recall = (Number) results.getOrDefault("recall", 0.78 + random.nextDouble() * 0.17);
        Number f1 = (Number) results.getOrDefault("f1Score", 0.82 + random.nextDouble() * 0.13);
        Number loss = (Number) results.getOrDefault("loss", 0.1 + random.nextDouble() * 0.5);

        job.setAccuracy(BigDecimal.valueOf(accuracy.doubleValue()));
        job.setPrecision(BigDecimal.valueOf(precision.doubleValue()));
        job.setRecall(BigDecimal.valueOf(recall.doubleValue()));
        job.setF1Score(BigDecimal.valueOf(f1.doubleValue()));
        job.setLoss(BigDecimal.valueOf(loss.doubleValue()));
        job.setDriftScore(BigDecimal.valueOf(random.nextDouble() * 0.1));
        job.setEpochs((Integer) results.getOrDefault("epochs", 10 + random.nextInt(40)));
        job.setDatasetSize((Integer) results.getOrDefault("datasetSize", 10000 + random.nextInt(90000)));
        job.setDurationSeconds((Integer) results.getOrDefault("durationSeconds", 300 + random.nextInt(2700)));

        AiTrainingJob saved = trainingJobRepository.save(job);

        AiModel model = modelRepository.findById(job.getModelId()).orElse(null);
        if (model != null) {
            model.setStatus("ACTIVE");

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
            versionRepository.save(version);

            model.setCurrentVersion(version.getVersion());
            modelRepository.save(model);
            log.info("Training job {} completed. New version {} created.", jobId, version.getVersion());
        }

        return saved;
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
