package com.nexus.oms.service;

import com.nexus.oms.entity.NxSlottingAssignment;
import com.nexus.oms.repository.SlottingAssignmentRepository;
import com.nexus.oms.repository.SlottingAuditRepository;
import com.nexus.oms.repository.SlottingRuleRepository;
import com.nexus.oms.repository.WarehouseBinRepository;
import com.nexus.oms.repository.WarehouseZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlottingFeedbackTest {

    @Mock
    private SlottingAssignmentRepository assignmentRepository;
    @Mock
    private SlottingRuleRepository ruleRepository;
    @Mock
    private SlottingAuditRepository auditRepository;
    @Mock
    private WarehouseBinRepository binRepository;
    @Mock
    private WarehouseZoneRepository zoneRepository;

    private SlottingService service;

    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        service = new SlottingService(assignmentRepository, ruleRepository, auditRepository, binRepository, zoneRepository);
        warehouseId = UUID.randomUUID();
    }

    @Test
    void recordPick_incrementsPickFrequencyAndSetsLastPickedAt() {
        NxSlottingAssignment a = assignment("SKU-001", 2);
        when(assignmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(a));
        when(assignmentRepository.save(any(NxSlottingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordPick(warehouseId, "SKU-001");

        assertThat(a.getPickFrequency()).isEqualTo(3);
        assertThat(a.getLastPickedAt()).isNotNull();
        verify(assignmentRepository).save(a);
    }

    @Test
    void recordPick_initializesFrequencyWhenNull() {
        NxSlottingAssignment a = assignment("SKU-001", null);
        when(assignmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(a));
        when(assignmentRepository.save(any(NxSlottingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordPick(warehouseId, "SKU-001");

        assertThat(a.getPickFrequency()).isEqualTo(1);
        assertThat(a.getLastPickedAt()).isNotNull();
    }

    @Test
    void recordPick_ignoresOtherSkus() {
        NxSlottingAssignment a = assignment("SKU-OTHER", 4);
        when(assignmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(a));

        service.recordPick(warehouseId, "SKU-001");

        assertThat(a.getPickFrequency()).isEqualTo(4);
    }

    @Test
    void recordPutaway_updatesBinAndLastSlottingAt() {
        NxSlottingAssignment a = assignment("SKU-001", 1);
        UUID binId = UUID.randomUUID();
        when(assignmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(a));
        when(assignmentRepository.save(any(NxSlottingAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordPutaway(warehouseId, "SKU-001", binId);

        assertThat(a.getBinId()).isEqualTo(binId);
        assertThat(a.getLastSlottingAt()).isNotNull();
        verify(assignmentRepository).save(a);
    }

    private NxSlottingAssignment assignment(String sku, Integer pickFrequency) {
        NxSlottingAssignment a = new NxSlottingAssignment();
        a.setId(UUID.randomUUID());
        a.setWarehouseId(warehouseId);
        a.setSku(sku);
        a.setBinId(UUID.randomUUID());
        a.setAssignedQuantity(10);
        a.setPickFrequency(pickFrequency);
        return a;
    }
}
