package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.AiDeployment;
import com.nexus.oms.entity.ai.AiModelVersion;
import com.nexus.oms.repository.ai.AiDeploymentRepository;
import com.nexus.oms.repository.ai.AiModelVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P1.4 gate contract tests:
 *  - missing metrics fail closed
 *  - absolute ceilings enforced (WAPE <= 0.60, MASE < 1.0)
 *  - champion-challenger relative gate is asymmetric (needs >= 5% improvement)
 *  - first deployment (no champion) passes on absolute gates alone
 */
@ExtendWith(MockitoExtension.class)
class AiDeploymentGateServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID MODEL = UUID.randomUUID();

    @Mock private AiDeploymentRepository deploymentRepository;
    @Mock private AiModelVersionRepository versionRepository;

    private AiDeploymentGateService gate;

    @BeforeEach
    void setUp() {
        gate = new AiDeploymentGateService(deploymentRepository, versionRepository,
                new ObjectMapper(), 0.60, 1.0, 0.05);
    }

    private AiModelVersion versionWithMetrics(String metricsJson) {
        AiModelVersion v = new AiModelVersion();
        v.setId(UUID.randomUUID());
        v.setModelId(MODEL);
        v.setMetrics(metricsJson);
        return v;
    }

    private AiDeployment activeChampion(UUID versionId) {
        AiDeployment d = new AiDeployment();
        d.setId(UUID.randomUUID());
        d.setTenantId(TENANT);
        d.setModelId(MODEL);
        d.setVersionId(versionId);
        d.setEnvironment("PRODUCTION");
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void missingMetrics_failsClosed() {
        var result = gate.evaluate(TENANT, MODEL, versionWithMetrics(null), "PRODUCTION");
        assertFalse(result.passed());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("wape")));
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("mase")));
    }

    @Test
    void wapeOverCeiling_fails() {
        var result = gate.evaluate(TENANT, MODEL,
                versionWithMetrics("{\"wape\":0.75,\"mase\":0.9}"), "PRODUCTION");
        assertFalse(result.passed());
        assertTrue(result.failures().get(0).contains("exceeds absolute ceiling"));
    }

    @Test
    void maseAtOrAboveOne_fails() {
        var result = gate.evaluate(TENANT, MODEL,
                versionWithMetrics("{\"wape\":0.40,\"mase\":1.0}"), "PRODUCTION");
        assertFalse(result.passed());
        assertTrue(result.failures().get(0).contains("seasonal-naive"));
    }

    @Test
    void noChampion_goodMetrics_passes() {
        when(deploymentRepository.findAllByTenantIdAndModelIdAndEnvironment(TENANT, MODEL, "PRODUCTION"))
                .thenReturn(List.of());

        var result = gate.evaluate(TENANT, MODEL,
                versionWithMetrics("{\"wape\":0.42,\"mase\":0.87}"), "PRODUCTION");

        assertTrue(result.passed());
        assertEquals("No active champion - absolute gates only", result.detail().get("note"));
    }

    @Test
    void challengerNotBeatingChampionByMargin_fails() {
        UUID champVersion = UUID.randomUUID();
        AiModelVersion champion = versionWithMetrics("{\"wape\":0.50,\"mase\":0.9}");
        when(deploymentRepository.findAllByTenantIdAndModelIdAndEnvironment(TENANT, MODEL, "PRODUCTION"))
                .thenReturn(List.of(activeChampion(champVersion)));
        when(versionRepository.findById(champVersion)).thenReturn(Optional.of(champion));

        // 0.48 < 0.50 but improvement is only 4% (< required 5%)
        var result = gate.evaluate(TENANT, MODEL,
                versionWithMetrics("{\"wape\":0.48,\"mase\":0.85}"), "PRODUCTION");

        assertFalse(result.passed());
        assertTrue(result.failures().get(0).contains("does not improve on champion"));
    }

    @Test
    void challengerBeatingChampionByMargin_passes() {
        UUID champVersion = UUID.randomUUID();
        AiModelVersion champion = versionWithMetrics("{\"wape\":0.50,\"mase\":0.9}");
        when(deploymentRepository.findAllByTenantIdAndModelIdAndEnvironment(TENANT, MODEL, "PRODUCTION"))
                .thenReturn(List.of(activeChampion(champVersion)));
        when(versionRepository.findById(champVersion)).thenReturn(Optional.of(champion));

        // 0.44 is a 12% improvement over 0.50
        var result = gate.evaluate(TENANT, MODEL,
                versionWithMetrics("{\"wape\":0.44,\"mase\":0.80}"), "PRODUCTION");

        assertTrue(result.passed());
        assertEquals(0.50, (Double) result.detail().get("championWape"), 1e-9);
    }

    @Test
    void championWithoutWape_skipsRelativeGate() {
        UUID champVersion = UUID.randomUUID();
        AiModelVersion legacyChampion = versionWithMetrics(null); // predates gate contract
        when(deploymentRepository.findAllByTenantIdAndModelIdAndEnvironment(eq(TENANT), eq(MODEL), any()))
                .thenReturn(List.of(activeChampion(champVersion)));
        when(versionRepository.findById(champVersion)).thenReturn(Optional.of(legacyChampion));

        var result = gate.evaluate(TENANT, MODEL,
                versionWithMetrics("{\"wape\":0.42,\"mase\":0.87}"), "PRODUCTION");

        assertTrue(result.passed());
    }
}
