package com.nexus.oms.service.ai;

import com.nexus.oms.entity.ai.AiExperiment;
import com.nexus.oms.repository.ai.AiExperimentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AiExperimentService {

    private static final Logger log = LoggerFactory.getLogger(AiExperimentService.class);

    private final AiExperimentRepository repository;

    public AiExperimentService(AiExperimentRepository repository) {
        this.repository = repository;
    }

    public Page<AiExperiment> getExperiments(UUID tenantId, UUID modelId, String status, Pageable pageable) {
        if (modelId != null) {
            return repository.findByModelId(modelId, pageable);
        }
        if (status != null && !status.isBlank()) {
            return repository.findByTenantId(tenantId, pageable);
        }
        return repository.findByTenantId(tenantId, pageable);
    }

    public AiExperiment getExperiment(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Experiment not found: " + id));
    }

    @Transactional
    public AiExperiment createExperiment(AiExperiment experiment) {
        experiment.setStatus("DRAFT");
        experiment.setCreatedAt(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());
        AiExperiment saved = repository.save(experiment);
        log.info("Created experiment: {} ({})", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public AiExperiment updateExperiment(UUID id, AiExperiment updates) {
        AiExperiment existing = getExperiment(id);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getExperimentType() != null) existing.setExperimentType(updates.getExperimentType());
        if (updates.getModelId() != null) existing.setModelId(updates.getModelId());
        if (updates.getChampionVersionId() != null) existing.setChampionVersionId(updates.getChampionVersionId());
        if (updates.getChallengerVersionId() != null) existing.setChallengerVersionId(updates.getChallengerVersionId());
        if (updates.getTrafficSplit() != null) existing.setTrafficSplit(updates.getTrafficSplit());
        if (updates.getSuccessMetric() != null) existing.setSuccessMetric(updates.getSuccessMetric());
        existing.setUpdatedAt(LocalDateTime.now());
        return repository.save(existing);
    }

    @Transactional
    public AiExperiment startExperiment(UUID id) {
        AiExperiment exp = getExperiment(id);
        if (!"DRAFT".equals(exp.getStatus())) {
            throw new IllegalStateException("Only DRAFT experiments can be started. Current status: " + exp.getStatus());
        }
        if (exp.getChampionVersionId() == null || exp.getChallengerVersionId() == null) {
            throw new IllegalStateException("Both champion and challenger versions must be set before starting");
        }
        exp.setStatus("RUNNING");
        exp.setStartDate(LocalDateTime.now());
        exp.setUpdatedAt(LocalDateTime.now());
        return repository.save(exp);
    }

    @Transactional
    public AiExperiment completeExperiment(UUID id, UUID winnerVersionId) {
        AiExperiment exp = getExperiment(id);
        if (!"RUNNING".equals(exp.getStatus())) {
            throw new IllegalStateException("Only RUNNING experiments can be completed. Current status: " + exp.getStatus());
        }
        exp.setStatus("COMPLETED");
        exp.setWinnerVersionId(winnerVersionId);
        exp.setEndDate(LocalDateTime.now());
        exp.setUpdatedAt(LocalDateTime.now());
        return repository.save(exp);
    }

    @Transactional
    public AiExperiment rollbackExperiment(UUID id) {
        AiExperiment exp = getExperiment(id);
        if (!"RUNNING".equals(exp.getStatus()) && !"COMPLETED".equals(exp.getStatus())) {
            throw new IllegalStateException("Only RUNNING or COMPLETED experiments can be rolled back. Current status: " + exp.getStatus());
        }
        exp.setStatus("ROLLED_BACK");
        exp.setEndDate(LocalDateTime.now());
        exp.setUpdatedAt(LocalDateTime.now());
        return repository.save(exp);
    }

    @Transactional
    public AiExperiment failExperiment(UUID id, String errorMessage) {
        AiExperiment exp = getExperiment(id);
        exp.setStatus("FAILED");
        exp.setEndDate(LocalDateTime.now());
        exp.setUpdatedAt(LocalDateTime.now());
        exp.setResults("{\"error\": \"" + (errorMessage != null ? errorMessage.replace("\"", "\\\"") : "Unknown error") + "\"}");
        return repository.save(exp);
    }
}
