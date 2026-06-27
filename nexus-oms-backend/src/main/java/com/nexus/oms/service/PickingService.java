package com.nexus.oms.service;

import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PicklistItemRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PickingService {

    private final PicklistRepository picklistRepository;
    private final PicklistItemRepository picklistItemRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;

    public PickingService(PicklistRepository picklistRepository,
                          PicklistItemRepository picklistItemRepository,
                          WarehouseStaffRepository warehouseStaffRepository) {
        this.picklistRepository = picklistRepository;
        this.picklistItemRepository = picklistItemRepository;
        this.warehouseStaffRepository = warehouseStaffRepository;
    }

    public List<NxPicklist> getPicklists(UUID tenantId) {
        return picklistRepository.findByTenantId(tenantId);
    }

    public List<NxPicklist> getPicklistsByStatus(UUID tenantId, String status) {
        return picklistRepository.findByTenantIdAndStatus(tenantId, status);
    }

    public NxPicklist getPicklist(UUID id) {
        return picklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Picklist", id));
    }

    public List<NxPicklistItem> getPicklistItems(UUID picklistId) {
        return picklistItemRepository.findByPicklistId(picklistId);
    }

    @Transactional
    public NxPicklist createPicklist(NxPicklist picklist) {
        if (picklist.getTotalItems() == null) picklist.setTotalItems(0);
        if (picklist.getPickedItems() == null) picklist.setPickedItems(0);
        return picklistRepository.save(picklist);
    }

    @Transactional
    public NxPicklist assignPicker(UUID picklistId, UUID staffId) {
        NxPicklist pl = getPicklist(picklistId);
        pl.setAssigneeId(staffId);
        pl.setStatus("IN_PROGRESS");
        pl.setStartedAt(LocalDateTime.now());
        return picklistRepository.save(pl);
    }

    @Transactional
    public NxPicklistItem pickItem(UUID itemId, UUID staffId) {
        NxPicklistItem item = picklistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PicklistItem", itemId));
        item.setStatus("PICKED");
        item.setPickedQuantity(item.getQuantity());
        item.setPickedBy(staffId);
        item.setPickedAt(LocalDateTime.now());
        picklistItemRepository.save(item);

        // Update picklist counts
        NxPicklist pl = getPicklist(item.getPicklistId());
        pl.setPickedItems(pl.getPickedItems() + 1);
        if (pl.getPickedItems() >= pl.getTotalItems()) {
            pl.setStatus("COMPLETED");
            pl.setCompletedAt(LocalDateTime.now());
        }
        picklistRepository.save(pl);

        // Increment staff pick count
        warehouseStaffRepository.findById(staffId).ifPresent(staff -> {
            staff.setItemsPickedToday(staff.getItemsPickedToday() + 1);
            warehouseStaffRepository.save(staff);
        });

        return item;
    }

    @Transactional
    public NxPicklist completePicklist(UUID picklistId) {
        NxPicklist pl = getPicklist(picklistId);
        pl.setStatus("COMPLETED");
        pl.setCompletedAt(LocalDateTime.now());
        pl.setPickedItems(pl.getTotalItems());
        // Mark all pending items as picked
        List<NxPicklistItem> remaining = picklistItemRepository.findByPicklistIdAndStatus(picklistId, "PENDING");
        for (NxPicklistItem item : remaining) {
            item.setStatus("PICKED");
            item.setPickedQuantity(item.getQuantity());
            picklistItemRepository.save(item);
        }
        return picklistRepository.save(pl);
    }

    @Transactional
    public NxPicklist cancelPicklist(UUID picklistId) {
        NxPicklist pl = getPicklist(picklistId);
        pl.setStatus("CANCELLED");
        List<NxPicklistItem> items = picklistItemRepository.findByPicklistId(picklistId);
        for (NxPicklistItem item : items) {
            if (!"PICKED".equals(item.getStatus())) {
                item.setStatus("CANCELLED");
                picklistItemRepository.save(item);
            }
        }
        return picklistRepository.save(pl);
    }

    public Map<String, Object> getDashboardKPIs(UUID tenantId) {
        long activePicklists = picklistRepository.countByTenantIdAndStatus(tenantId, "OPEN")
                + picklistRepository.countByTenantIdAndStatus(tenantId, "IN_PROGRESS");
        long completedToday = picklistRepository.countByTenantIdAndStatus(tenantId, "COMPLETED");
        long pendingItems = picklistItemRepository.countByTenantIdAndStatus(tenantId, "PENDING");
        long pickedItems = picklistItemRepository.countByTenantIdAndStatus(tenantId, "PICKED");

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("activePicklists", activePicklists);
        kpis.put("completedToday", completedToday);
        kpis.put("pendingItems", pendingItems);
        kpis.put("pickedItems", pickedItems);
        return kpis;
    }
}
