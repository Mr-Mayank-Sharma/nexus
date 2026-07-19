package com.nexus.oms.service;

import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PicklistItemRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickingServiceTest {

    @Mock
    private PicklistRepository picklistRepository;
    @Mock
    private PicklistItemRepository picklistItemRepository;
    @Mock
    private WarehouseStaffRepository warehouseStaffRepository;

    private PickingService pickingService;
    private UUID tenantId;
    private UUID picklistId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        pickingService = new PickingService(picklistRepository, picklistItemRepository, warehouseStaffRepository);
        tenantId = UUID.randomUUID();
        picklistId = UUID.randomUUID();
    }

    @Test
    void getPicklists() {
        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);
        when(picklistRepository.findByTenantId(tenantId)).thenReturn(List.of(pl));

        assertEquals(1, pickingService.getPicklists(tenantId).size());
    }

    @Test
    void getPicklistsByStatus() {
        when(picklistRepository.findByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(List.of(new NxPicklist()));

        assertEquals(1, pickingService.getPicklistsByStatus(tenantId, "OPEN").size());
    }

    @Test
    void getPicklist_found() {
        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);
        when(picklistRepository.findById(picklistId)).thenReturn(Optional.of(pl));

        NxPicklist result = pickingService.getPicklist(picklistId);

        assertEquals(picklistId, result.getId());
    }

    @Test
    void getPicklist_notFound_throws() {
        when(picklistRepository.findById(picklistId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pickingService.getPicklist(picklistId));
    }

    @Test
    void getPicklistItems() {
        NxPicklistItem item = new NxPicklistItem();
        when(picklistItemRepository.findByPicklistId(picklistId)).thenReturn(List.of(item));

        assertEquals(1, pickingService.getPicklistItems(picklistId).size());
    }

    @Test
    void createPicklist_setsDefaults() {
        NxPicklist input = new NxPicklist();
        when(picklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPicklist result = pickingService.createPicklist(input);

        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getPickedItems());
    }

    @Test
    void assignPicker() {
        UUID staffId = UUID.randomUUID();
        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);
        when(picklistRepository.findById(picklistId)).thenReturn(Optional.of(pl));
        when(picklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPicklist result = pickingService.assignPicker(picklistId, staffId);

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals(staffId, result.getAssigneeId());
        assertNotNull(result.getStartedAt());
    }

    @Test
    void pickItem_updatesItemAndPicklist() {
        UUID staffId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        NxPicklistItem item = new NxPicklistItem();
        item.setId(itemId);
        item.setPicklistId(picklistId);
        item.setQuantity(3);

        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);
        pl.setTotalItems(3);
        pl.setPickedItems(2);

        WarehouseStaff staff = new WarehouseStaff();
        staff.setId(staffId);
        staff.setItemsPickedToday(10);

        when(picklistItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(picklistRepository.findById(picklistId)).thenReturn(Optional.of(pl));
        when(picklistItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(picklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(warehouseStaffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        NxPicklistItem result = pickingService.pickItem(itemId, staffId);

        assertEquals("PICKED", result.getStatus());
        assertEquals(staffId, result.getPickedBy());
        assertNotNull(result.getPickedAt());
        verify(warehouseStaffRepository).save(any());
    }

    @Test
    void pickItem_completesPicklist() {
        UUID staffId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        NxPicklistItem item = new NxPicklistItem();
        item.setId(itemId);
        item.setPicklistId(picklistId);
        item.setQuantity(1);

        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);
        pl.setTotalItems(1);
        pl.setPickedItems(0);

        when(picklistItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(picklistRepository.findById(picklistId)).thenReturn(Optional.of(pl));
        when(picklistItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(picklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPicklistItem result = pickingService.pickItem(itemId, staffId);

        assertEquals("PICKED", result.getStatus());
    }

    @Test
    void pickItem_itemNotFound_throws() {
        when(picklistItemRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pickingService.pickItem(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void completePicklist() {
        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);
        pl.setTotalItems(2);
        pl.setPickedItems(1);

        NxPicklistItem pending1 = new NxPicklistItem();
        pending1.setQuantity(1);
        NxPicklistItem pending2 = new NxPicklistItem();
        pending2.setQuantity(2);

        when(picklistRepository.findById(picklistId)).thenReturn(Optional.of(pl));
        when(picklistItemRepository.findByPicklistIdAndStatus(picklistId, "PENDING"))
                .thenReturn(List.of(pending1, pending2));
        when(picklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPicklist result = pickingService.completePicklist(picklistId);

        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(picklistItemRepository, times(2)).save(any());
    }

    @Test
    void cancelPicklist() {
        NxPicklist pl = new NxPicklist();
        pl.setId(picklistId);

        NxPicklistItem picked = new NxPicklistItem();
        picked.setStatus("PICKED");
        NxPicklistItem pending = new NxPicklistItem();
        pending.setStatus("PENDING");

        when(picklistRepository.findById(picklistId)).thenReturn(Optional.of(pl));
        when(picklistItemRepository.findByPicklistId(picklistId)).thenReturn(List.of(picked, pending));
        when(picklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPicklist result = pickingService.cancelPicklist(picklistId);

        assertEquals("CANCELLED", result.getStatus());
        verify(picklistItemRepository, times(1)).save(any());
    }

    @Test
    void getDashboardKPIs() {
        when(picklistRepository.countByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(2L);
        when(picklistRepository.countByTenantIdAndStatus(tenantId, "IN_PROGRESS")).thenReturn(3L);
        when(picklistRepository.countByTenantIdAndStatus(tenantId, "COMPLETED")).thenReturn(5L);
        when(picklistItemRepository.countByTenantIdAndStatus(tenantId, "PENDING")).thenReturn(10L);
        when(picklistItemRepository.countByTenantIdAndStatus(tenantId, "PICKED")).thenReturn(20L);

        Map<String, Object> kpis = pickingService.getDashboardKPIs(tenantId);

        assertEquals(5L, kpis.get("activePicklists"));
        assertEquals(5L, kpis.get("completedToday"));
        assertEquals(10L, kpis.get("pendingItems"));
        assertEquals(20L, kpis.get("pickedItems"));
    }
}
