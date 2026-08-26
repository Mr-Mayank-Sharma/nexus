package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.AiDeployment;
import com.nexus.oms.entity.ai.AiModelVersion;
import com.nexus.oms.repository.ai.AiDeploymentRepository;
import com.nexus.oms.repository.ai.AiModelVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deployment validation gate (P1.4).
 *
 * A model version may ONLY go live if its rolling-origin backtest metrics
 * clear BOTH:
 *
 *   1. Absolute gates   — WAPE <= nexus.ai.gate.max-wape (default 0.60)
 *                         MASE <  nexus.ai.gate.max-mase (default 1.0,
 *                         i.e. strictly beats the seasonal-naive baseline)
 *   2. Relative gate    — when an ACTIVE champion exists for the same
 *                         (tenant, model, environment), the challenger's
 *                         WAPE must beat it by at least
 *                         nexus.ai.gate.min-improvement (default 5%).
 *                         Asymmetric by design: ties or marginal wins do
 *                         NOT replace a working champion.
 *
 * Metrics are read from ai_model_versions.metrics (JSONB). Expected shape
 * (written by the Python training pipeline after a rolling-origin backtest):
 *
 *   {"wape": 0.42, "mase": 0.87, "baseline_wape": 0.55,
 *    "backtest": {"method": "rolling_origin", "folds": 5}}
 *
 * Missing metrics = failed gate. No metrics, no deploy.
 */
@Service
public class AiDeploymentGateService {

    private static final Logger log = LoggerFactory.getLogger(AiDeploymentGateService.class);

    private final AiDeploymentRepository deploymentRepository;
    private final AiModelVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    private final double maxWape;
    private final double maxMase;
    private final double minImprovement;

    public AiDeploymentGateService(AiDeploymentRepository deploymentRepository,
                                   AiModelVersionRepository versionRepository,
                                   ObjectMapper objectMapper,
                                   @Value("${nexus.ai.gate.max-wape:0.60}") double maxWape,
                                   @Value("${nexus.ai.gate.max-mase:1.0}") double maxMase,
                                   @Value("${nexus.ai.gate.min-improvement:0.05}") double minImprovement) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.maxWape = maxWape;
        this.maxMase = maxMase;
        this.minImprovement = minImprovement;
    }

    /**
     * Result of gate evaluation. {@code passed == false} always carries
     * human-readable reasons; {@code detail} carries the numbers behind them.
     */
    public record GateResult(boolean passed, List<String> failures, Map<String, Object> detail) {
        static GateResult ok(Map<String, Object> detail) {
            return new GateResult(true, List.of(), detail);
        }

        static GateResult fail(List<String> failures, Map<String, Object> detail) {
            return new GateResult(false, List.copyOf(failures), detail);
        }
    }

    public GateResult evaluate(UUID tenantId, UUID modelId, AiModelVersion challenger, String environment) {
        List<String> failures = new ArrayList<>();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("environment", environment != null ? environment : "PRODUCTION");
        detail.put("thresholds", Map.of(
                "maxWape", maxWape,
                "maxMase", maxMase,
                "minImprovementVsChampion", minImprovement));

        JsonNode metrics = parseMetrics(challenger.getMetrics());
        Double challengerWape = requireMetric(metrics, "wape", failures);
        Double challengerMase = requireMetric(metrics, "mase", failures);

        if (challengerWape != null) {
            detail.put("challengerWape", challengerWape);
            if (challengerWape > maxWape) {
                failures.add(String.format("WAPE %.4f exceeds absolute ceiling %.4f", challengerWape, maxWape));
            }
        }
        if (challengerMase != null) {
            detail.put("challengerMase", challengerMase);
            if (!(challengerMase < maxMase)) {
                failures.add(String.format("MASE %.4f does not beat seasonal-naive baseline (must be < %.4f)",
                        challengerMase, maxMase));
            }
        }

        AiDeployment championDeployment = deploymentRepository
                .findAllByTenantIdAndModelIdAndEnvironment(tenantId, modelId,
                        environment != null ? environment : "PRODUCTION")
                .stream()
                .filter(d -> "ACTIVE".equals(d.getStatus()))
                .findFirst()
                .orElse(null);

        if (championDeployment != null) {
            detail.put("championVersionId", championDeployment.getVersionId().toString());
            versionRepository.findById(championDeployment.getVersionId()).ifPresentOrElse(champion -> {
                Double championWape = parseMetrics(champion.getMetrics()).path("wape").asDouble(Double.NaN);
                if (Double.isNaN(championWape)) {
                    // Champion predates the gate contract; absolute gates alone decide.
                    log.warn("Champion version {} has no WAPE metric; skipping relative gate",
                            champion.getId());
                    detail.put("championWape", null);
                } else {
                    double required = championWape * (1.0 - minImprovement);
                    detail.put("championWape", championWape);
                    detail.put("requiredChallengerWape", required);
                    if (challengerWape != null && challengerWape > required) {
                        failures.add(String.format(
                                "Challenger WAPE %.4f does not improve on champion %.4f by required %.1f%% (needs <= %.4f)",
                                challengerWape, championWape, minImprovement * 100, required));
                    }
                }
            }, () -> log.warn("Champion version {} missing from registry; skipping relative gate",
                    championDeployment.getVersionId()));
        } else {
            detail.put("championWape", null);
            detail.put("note", "No active champion - absolute gates only");
        }

        if (!failures.isEmpty()) {
            return GateResult.fail(failures, detail);
        }
        return GateResult.ok(detail);
    }

    private JsonNode parseMetrics(String metricsJson) {
        if (metricsJson == null || metricsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(metricsJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private Double requireMetric(JsonNode metrics, String name, List<String> failures) {
        JsonNode node = metrics.path(name);
        if (node.isMissingNode() || !node.isNumber()) {
            failures.add("Missing required backtest metric '" + name
                    + "' in version.metrics (expected from rolling-origin backtest)");
            return null;
        }
        return node.asDouble();
    }
}
