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
    private final AiDeploymentGateService gateService;

    /** Ramp ladder for progressive rollout: 5% → 10% → 25% → 50% → 100%. */
    private static final List<BigDecimal> RAMP_LADDER = List.of(
            new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.25"),
            new BigDecimal("0.50"), BigDecimal.ONE);

    public AiModelRegistryService(AiModelRepository modelRepository,
                                   AiModelVersionRepository versionRepository,
                                   AiDeploymentRepository deploymentRepository,
                                   AiModelMetricRepository metricRepository,
                                   AiDeploymentGateService gateService) {
        this.modelRepository = modelRepository;
        this.versionRepository = versionRepository;
        this.deploymentRepository = deploymentRepository;
        this.metricRepository = metricRepository;
        this.gateService = gateService;
    }

    public Page<AiModel> getModels(UUID tenantId, String category, String status, Pageable pageable) {
        if (category != null) return modelRepository.findAllForTenantAndCategory(tenantId, category, pageable);
        if (status != null) {
            if (tenantId != null) return modelRepository.findAllForTenantAndStatus(tenantId, status, pageable);
            return modelRepository.findByStatus(status, pageable);
        }
        return modelRepository.findAllForTenant(tenantId, pageable);
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

    /**
     * Deploy a version behind the P1.4 validation gate.
     *
     * Gate failure throws {@link AiGateBlockedException} unless {@code force}
     * is true (escape hatch for admins; the override is logged and stamped on
     * the version). On success, any existing ACTIVE deployment for the same
     * (tenant, model, environment) is marked SUPERSEDED — champion keeps
     * serving until the challenger actually beats it, never on ties.
     */
    @Transactional
    public AiDeployment deploy(UUID tenantId, UUID modelId, UUID versionId, String environment, boolean force) {
        AiModelVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        String env = environment != null ? environment : "PRODUCTION";

        AiDeploymentGateService.GateResult gate = gateService.evaluate(tenantId, modelId, version, env);
        boolean forced = !gate.passed();
        if (forced) {
            if (!force) {
                throw new AiGateBlockedException(gate.failures(), gate.detail());
            }
            log.warn("Deployment gate OVERRIDDEN by {} for model={} version={}: {}",
                    TenantContext.getCurrentUsername(), modelId, versionId, gate.failures());
        }

        version.setStatus("DEPLOYED");
        version.setValidatedBy(TenantContext.getCurrentUsername());
        version.setValidatedAt(LocalDateTime.now());
        version.setGateOverride(forced);
        version.setGateFailures(forced ? String.join("; ", gate.failures()) : null);
        version.setDeployedBy(TenantContext.getCurrentUsername());
        version.setDeployedAt(LocalDateTime.now());
        versionRepository.save(version);

        // Supersede current champion(s) - history preserved as separate rows.
        List<AiDeployment> incumbents = deploymentRepository
                .findAllByTenantIdAndModelIdAndEnvironment(tenantId, modelId, env);
        boolean hadChampion = false;
        for (AiDeployment incumbent : incumbents) {
            if ("ACTIVE".equals(incumbent.getStatus())) {
                incumbent.setStatus("SUPERSEDED");
                deploymentRepository.save(incumbent);
                hadChampion = true;
            }
        }

        AiDeployment deployment = AiDeployment.builder()
                .tenantId(tenantId)
                .modelId(modelId)
                .versionId(versionId)
                .environment(env)
                .trafficWeight(BigDecimal.ONE)
                .status("ACTIVE")
                .deployedBy(TenantContext.getCurrentUsername())
                .build();
        return deploymentRepository.save(deployment);
    }

    /**
     * Progressive rollout: bump the ACTIVE deployment's traffic weight along
     * the 5% → 10% → 25% → 50% → 100% ladder. Bookkeeping today (the gateway
     * serves the single ACTIVE deployment regardless of weight); wired into
     * weighted serving once real traffic splitting lands post-P1.6.
     */
    @Transactional
    public AiDeployment ramp(UUID tenantId, UUID modelId, UUID versionId) {
        AiDeployment deployment = deploymentRepository
                .findAllByTenantIdAndModelIdAndEnvironment(tenantId, modelId, "PRODUCTION")
                .stream()
                .filter(d -> versionId.equals(d.getVersionId()))
                .filter(d -> "ACTIVE".equals(d.getStatus()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No ACTIVE deployment of version " + versionId + " to ramp"));

        BigDecimal current = deployment.getTrafficWeight() == null ? BigDecimal.ZERO : deployment.getTrafficWeight();
        BigDecimal next = RAMP_LADDER.stream()
                .filter(rung -> rung.compareTo(current) > 0)
                .findFirst()
                .orElse(BigDecimal.ONE);
        deployment.setTrafficWeight(next);
        log.info("Ramping deployment {} to {}", deployment.getId(), next);
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
