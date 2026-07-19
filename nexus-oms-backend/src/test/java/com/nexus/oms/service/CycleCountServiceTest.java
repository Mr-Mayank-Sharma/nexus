package com.nexus.oms.service;

import com.nexus.oms.dto.CycleCountRequest;
import com.nexus.oms.entity.NxCycleCount;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxCycleCountRepository;
import com.nexus.oms.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CycleCountServiceTest {

    @Mock
    private NxCycleCountRepository cycleCountRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    private CycleCountService cycleCountService;
    private UUID tenantId;
    private UUID countId;

    @BeforeEach
    void setUp() {
        cycleCountService = new CycleCountService(cycleCountRepository, inventoryRepository);
        tenantId = UUID.randomUUID();
        countId = UUID.randomUUID();
    }

    @Test
    void getCycleCounts_withStatus() {
        Page<NxCycleCount> page = new PageImpl<>(List.of(new NxCycleCount()));
        when(cycleCountRepository.findByTenantIdAndStatus(tenantId, "PENDING", Pageable.unpaged())).thenReturn(page);

        assertSame(page, cycleCountService.getCycleCounts(tenantId, "PENDING", Pageable.unpaged()));
    }

    @Test
    void getCycleCounts_withoutStatus() {
        Page<NxCycleCount> page = new PageImpl<>(List.of(new NxCycleCount()));
        when(cycleCountRepository.findByTenantId(tenantId, Pageable.unpaged())).thenReturn(page);

        assertSame(page, cycleCountService.getCycleCounts(tenantId, null, Pageable.unpaged()));
    }

    @Test
    void getCycleCounts_withBlankStatus() {
        Page<NxCycleCount> page = new PageImpl<>(List.of(new NxCycleCount()));
        when(cycleCountRepository.findByTenantId(tenantId, Pageable.unpaged())).thenReturn(page);

        assertSame(page, cycleCountService.getCycleCounts(tenantId, "  ", Pageable.unpaged()));
    }

    @Test
    void getCycleCount_found() {
        NxCycleCount cc = new NxCycleCount();
        cc.setId(countId);
        when(cycleCountRepository.findById(countId)).thenReturn(Optional.of(cc));

        assertSame(cc, cycleCountService.getCycleCount(countId));
    }

    @Test
    void getCycleCount_notFound_throws() {
        when(cycleCountRepository.findById(countId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cycleCountService.getCycleCount(countId));
    }

    @Test
    void createCycleCount() {
        CycleCountRequest request = new CycleCountRequest();
        request.setNodeId(UUID.randomUUID());
        request.setSku("SKU-001");
        request.setProductName("Test Product");
        request.setExpectedQty(10);
        request.setNotes("Test count");

        when(cycleCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxCycleCount result = cycleCountService.createCycleCount(tenantId, request);

        assertEquals(tenantId, result.getTenantId());
        assertEquals(request.getNodeId(), result.getNodeId());
        assertEquals("SKU-001", result.getSku());
        assertEquals(10, result.getExpectedQty());
        assertEquals("PENDING", result.getStatus());
        assertEquals("Test count", result.getNotes());
    }

    @Test
    void performCount_match() {
        NxCycleCount cc = new NxCycleCount();
        cc.setId(countId);
        cc.setTenantId(tenantId);
        cc.setSku("SKU-001");
        cc.setNodeId(UUID.randomUUID());
        cc.setExpectedQty(5);
        cc.setStatus("PENDING");

        when(cycleCountRepository.findById(countId)).thenReturn(Optional.of(cc));
        when(cycleCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxCycleCount result = cycleCountService.performCount(countId, 5, "Alice");

        assertEquals("MATCH", result.getStatus());
        assertEquals(5, result.getCountedQty());
        assertEquals("Alice", result.getCountedBy());
        assertNotNull(result.getCountedAt());
    }

    @Test
    void performCount_mismatch_updatesInventory() {
        UUID nodeId = UUID.randomUUID();
        NxCycleCount cc = new NxCycleCount();
        cc.setId(countId);
        cc.setTenantId(tenantId);
        cc.setSku("SKU-001");
        cc.setNodeId(nodeId);
        cc.setExpectedQty(5);
        cc.setStatus("PENDING");

        NxInventory inv = new NxInventory();
        inv.setQuantityOnHand(5);

        when(cycleCountRepository.findById(countId)).thenReturn(Optional.of(cc));
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, "SKU-001", nodeId))
                .thenReturn(Optional.of(inv));
        when(cycleCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxCycleCount result = cycleCountService.performCount(countId, 3, "Bob");

        assertEquals("MISMATCH", result.getStatus());
        assertEquals(3, result.getCountedQty());
        assertEquals(Integer.valueOf(3), inv.getQuantityOnHand());
        verify(inventoryRepository).save(inv);
    }

    @Test
    void performCount_notPending_throws() {
        NxCycleCount cc = new NxCycleCount();
        cc.setStatus("MATCH");
        when(cycleCountRepository.findById(countId)).thenReturn(Optional.of(cc));

        assertThrows(IllegalStateException.class, () -> cycleCountService.performCount(countId, 5, "Alice"));
    }

    @Test
    void performCount_mismatch_noExistingInventory() {
        NxCycleCount cc = new NxCycleCount();
        cc.setId(countId);
        cc.setTenantId(tenantId);
        cc.setSku("SKU-001");
        cc.setNodeId(UUID.randomUUID());
        cc.setExpectedQty(5);
        cc.setStatus("PENDING");

        when(cycleCountRepository.findById(countId)).thenReturn(Optional.of(cc));
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(cycleCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxCycleCount result = cycleCountService.performCount(countId, 2, "Charlie");

        assertEquals("MISMATCH", result.getStatus());
        verify(inventoryRepository, never()).save(any());
    }
}
