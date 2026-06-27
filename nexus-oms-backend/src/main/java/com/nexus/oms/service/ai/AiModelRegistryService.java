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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiModelRegistryService {

    private static final Logger log = LoggerFactory.getLogger(AiModelRegistryService.class);

    private final AiModelRepository modelRepository;
    private final AiModelVersionRepository versionRepository;
    private final AiDeploymentRepository deploymentRepository;
    private final AiModelMetricRepository metricRepository;

    public AiModelRegistryService(AiModelRepository modelRepository,
                                   AiModelVersionRepository versionRepository,
                                   AiDeploymentRepository deploymentRepository,
                                   AiModelMetricRepository metricRepository) {
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.deploymentRepository = deploymentRepository;
        this.metricRepository = metricRepository;
    }

    public Page<AiModel> getModels(UUID tenantId, String category, String status, Pageable pageable) {
        if (category != null) return modelRepository.findByTenantIdAndCategory(tenantId, category, pageable);
        if (status != null) {
            if (tenantId != null) return modelRepository.findByTenantIdAndStatus(tenantId, status, pageable);
            return modelRepository.findByStatus(status, pageable);
        }
        return modelRepository.findByTenantId(tenantId, pageable);
    }

    public Optional<AiModel> getModel(UUID modelId) {
        return modelRepository.findById(modelId);
    }

    @Transactional
    public AiModel createModel(AiModel model) {
        model.setIsActive(true);
        model.setStatus("DRAFT");
        model.setCreatedBy(TenantContext.getCurrentUsername());
        return modelRepository.save(model);
    }

    @Transactional
    public AiModel updateModel(UUID modelId, AiModel updates) {
        AiModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new NoSuchElementException("Model not found: " + modelId));
        if (updates.getDisplayName() != null) model.setDisplayName(updates.getDisplayName());
        if (updates.getDescription() != null) model.setDescription(updates.getDescription());
        if (updates.getConfig() != null) model.setConfig(updates.getConfig());
        if (updates.getStatus() != null) model.setStatus(updates.getStatus());
        if (updates.getInputSchema() != null) model.setInputSchema(updates.getInputSchema());
        if (updates.getOutputSchema() != null) model.setOutputSchema(updates.getOutputSchema());
        if (updates.getIsActive() != null) model.setIsActive(updates.getIsActive());
        return modelRepository.save(model);
    }

    public List<AiModelVersion> getVersions(UUID modelId) {
        return versionRepository.findByModelIdOrderByCreatedAtDesc(modelId);
    }

    @Transactional
    public AiModelVersion createVersion(UUID modelId, AiModelVersion version) {
        version.setModelId(modelId);
        String v = version.getVersion();
        if (v == null || v.isBlank()) {
            long count = versionRepository.countByModelId(modelId);
            version.setVersion("v" + (count + 1) + ".0.0");
        }
        version.setStatus("STAGED");
        version.setCreatedBy(TenantContext.getCurrentUsername());
        AiModelVersion saved = versionRepository.save(version);

        AiModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new NoSuchElementException("Model not found: " + modelId));
        model.setCurrentVersion(saved.getVersion());
        modelRepository.save(model);
        return saved;
    }

    @Transactional
    public AiDeployment deploy(UUID tenantId, UUID modelId, UUID versionId, String environment) {
        AiModelVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        version.setStatus("DEPLOYED");
        version.setDeployedBy(TenantContext.getCurrentUsername());
        version.setDeployedAt(LocalDateTime.now());
        versionRepository.save(version);

        AiDeployment existing = deploymentRepository
                .findByTenantIdAndModelIdAndEnvironment(tenantId, modelId, environment)
                .orElse(null);
        if (existing != null) {
            existing.setVersionId(versionId);
            existing.setStatus("ACTIVE");
            existing.setTrafficWeight(new BigDecimal("1.00"));
            return deploymentRepository.save(existing);
        }

        AiDeployment deployment = AiDeployment.builder()
                .tenantId(tenantId)
                .modelId(modelId)
                .versionId(versionId)
                .environment(environment != null ? environment : "PRODUCTION")
                .trafficWeight(new BigDecimal("1.00"))
                .status("ACTIVE")
                .deployedBy(TenantContext.getCurrentUsername())
                .build();
        return deploymentRepository.save(deployment);
    }

    @Transactional
    public void rollback(UUID tenantId, UUID modelId, UUID versionId) {
        List<AiDeployment> deployments = deploymentRepository.findByTenantIdAndModelId(tenantId, modelId);
        for (AiDeployment d : deployments) {
            d.setStatus("ROLLED_BACK");
            deploymentRepository.save(d);
        }

        AiModelVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));
        AiDeployment rollbackDep = AiDeployment.builder()
                .tenantId(tenantId)
                .modelId(modelId)
                .versionId(versionId)
                .environment("PRODUCTION")
                .trafficWeight(new BigDecimal("1.00"))
                .status("ACTIVE")
                .deployedBy(TenantContext.getCurrentUsername())
                .build();
        deploymentRepository.save(rollbackDep);

        version.setStatus("DEPLOYED");
        versionRepository.save(version);
    }

    public Map<String, Object> getRegistrySummary(UUID tenantId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalModels", modelRepository.countByTenantIdAndStatus(tenantId, "ACTIVE")
                + modelRepository.countByCategory("GLOBAL"));
        summary.put("activeModels", modelRepository.countByTenantIdAndStatus(tenantId, "ACTIVE"));
        summary.put("globalModels", modelRepository.countByCategory("GLOBAL"));
        summary.put("tenantModels", modelRepository.countByTenantIdAndCategory(tenantId, "TENANT"));
        summary.put("hybridModels", modelRepository.countByTenantIdAndCategory(tenantId, "HYBRID"));
        summary.put("modelsInTraining", modelRepository.countByTenantIdAndStatus(tenantId, "TRAINING"));
        return summary;
    }
}
