package com.nexus.oms.service;

import com.nexus.oms.entity.NxATPRule;
import com.nexus.oms.entity.NxATPSnapshot;
import com.nexus.oms.repository.ATPRuleRepository;
import com.nexus.oms.repository.ATPSnapshotRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ATPCalculationEngineTest {

    @Mock private ATPRuleRepository atpRuleRepository;
    @Mock private ATPSnapshotRepository atpSnapshotRepository;

    private ATPCalculationEngine engine;
    private UUID tenantId;
    private UUID nodeId;

    @BeforeEach
    void setUp() {
        engine = new ATPCalculationEngine(atpRuleRepository, atpSnapshotRepository);
        tenantId = UUID.randomUUID();
        nodeId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxATPSnapshot snapshot(int physical, int reserved, int allocated, int demand) {
        return NxATPSnapshot.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).nodeId(nodeId).sku("SKU-1")
                .physicalStock(physical).reservedStock(reserved).allocatedStock(allocated)
                .atpQuantity(Math.max(0, physical - reserved - allocated))
                .totalDemand(demand).netATP(Math.max(0, physical - reserved - allocated - demand))
                .build();
    }

    private List<NxATPRule> safetyAndHardReserveRules() {
        NxATPRule safety = NxATPRule.builder().name("10% safety").ruleType("SAFETY_STOCK")
                .safetyStockPercentage(new BigDecimal("10")).priority(1).active(true).build();
        NxATPRule hardReserve = NxATPRule.builder().name("hard reserve").ruleType("HARD_RESERVE")
                .reserveWindowHours(24).priority(2).active(true).build();
        return List.of(safety, hardReserve);
    }

    @Test
    void calculateATP_appliesSafetyAndHardReserveAndReturnsNet() {
        when(atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, "SKU-1"))
                .thenReturn(snapshot(100, 0, 0, 20));
        when(atpRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(safetyAndHardReserveRules());

        Map<String, Object> result = engine.calculateATP(nodeId, "SKU-1");

        assertEquals(10, result.get("safetyStock"));          // ceil(100 * 10%)
        assertEquals(10, result.get("reservedStock"));        // max(0, physical/10)
        assertEquals(80, result.get("atpQuantity"));          // 100 - 10 - 10 - 0
        assertEquals(60, result.get("netATP"));               // 80 - 20 demand
        assertEquals(true, result.get("available"));
        assertEquals(2, result.get("rulesApplied"));

        ArgumentCaptor<NxATPSnapshot> captor = ArgumentCaptor.forClass(NxATPSnapshot.class);
        verify(atpSnapshotRepository).save(captor.capture());
        assertEquals(80, captor.getValue().getAtpQuantity());
        assertEquals(60, captor.getValue().getNetATP());
    }

    @Test
    void calculateATP_noSnapshotReturnsUnavailable() {
        when(atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, "SKU-X"))
                .thenReturn(null);

        Map<String, Object> result = engine.calculateATP(nodeId, "SKU-X");

        assertEquals(false, result.get("available"));
        assertEquals(0, result.get("atpQuantity"));
        assertTrue(result.get("message").toString().contains("No inventory snapshot"));
        verify(atpSnapshotRepository, never()).save(any());
    }

    @Test
    void reserveStock_succeedsWhenSufficientAndPersistsNewSnapshot() {
        when(atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, "SKU-1"))
                .thenReturn(snapshot(100, 0, 0, 0));
        when(atpRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of());

        boolean ok = engine.reserveStock(nodeId, "SKU-1", 50);

        assertTrue(ok);
        ArgumentCaptor<NxATPSnapshot> captor = ArgumentCaptor.forClass(NxATPSnapshot.class);
        verify(atpSnapshotRepository).save(captor.capture());
        assertEquals(50, captor.getValue().getReservedStock());
        assertEquals(50, captor.getValue().getAtpQuantity());
    }

    @Test
    void reserveStock_rejectsWhenInsufficientAtp() {
        when(atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, "SKU-1"))
                .thenReturn(snapshot(100, 0, 0, 0));
        when(atpRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of());

        boolean ok = engine.reserveStock(nodeId, "SKU-1", 200);

        assertFalse(ok);
        verify(atpSnapshotRepository, never()).save(any());
    }

    @Test
    void findNodesWithATP_returnsOnlyNodesMeetingRequiredQuantitySortedByAtp() {
        NxATPSnapshot big = NxATPSnapshot.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .nodeId(UUID.randomUUID()).sku("SKU-1").physicalStock(100).reservedStock(0)
                .allocatedStock(0).totalDemand(0).build();
        NxATPSnapshot small = NxATPSnapshot.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .nodeId(UUID.randomUUID()).sku("SKU-1").physicalStock(30).reservedStock(0)
                .allocatedStock(0).totalDemand(0).build();
        when(atpSnapshotRepository.findBySku(eq(tenantId), eq("SKU-1"))).thenReturn(List.of(small, big));
        when(atpRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of());

        List<Map<String, Object>> nodes = engine.findNodesWithATP("SKU-1", 50);

        assertEquals(1, nodes.size());
        assertEquals(big.getNodeId(), nodes.get(0).get("nodeId"));
        assertEquals(100, nodes.get(0).get("atpQuantity"));
    }

    @Test
    void updateSnapshot_recomputesSafetyAndAtpFromNewPhysical() {
        when(atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, "SKU-1"))
                .thenReturn(null);
        when(atpRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(
                NxATPRule.builder().ruleType("SAFETY_STOCK").safetyStockPercentage(new BigDecimal("10"))
                        .active(true).build()));
        when(atpSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxATPSnapshot saved = engine.updateSnapshot(nodeId, "SKU-1", 50);

        assertEquals(50, saved.getPhysicalStock());
        assertEquals(5, saved.getSafetyStock());
        assertEquals(45, saved.getAtpQuantity());
        assertEquals(45, saved.getNetATP());
        verify(atpSnapshotRepository).save(any());
    }

    @Test
    void releaseReservation_returnsStockAndBoostsAtp() {
        when(atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, "SKU-1"))
                .thenReturn(snapshot(100, 50, 0, 0));

        engine.releaseReservation(nodeId, "SKU-1", 20);

        ArgumentCaptor<NxATPSnapshot> captor = ArgumentCaptor.forClass(NxATPSnapshot.class);
        verify(atpSnapshotRepository).save(captor.capture());
        assertEquals(30, captor.getValue().getReservedStock());
        assertEquals(70, captor.getValue().getAtpQuantity());
    }
}
