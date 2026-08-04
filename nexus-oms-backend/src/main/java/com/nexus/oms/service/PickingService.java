package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
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
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public PickingService(PicklistRepository picklistRepository,
                          PicklistItemRepository picklistItemRepository,
                          WarehouseStaffRepository warehouseStaffRepository,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository) {
        this.picklistRepository = picklistRepository;
        this.picklistItemRepository = picklistItemRepository;
        this.warehouseStaffRepository = warehouseStaffRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
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
    public NxPicklist createPicklistFromOrder(UUID orderId) {
        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        List<NxOrderItem> items = orderItemRepository.findByOrderId(orderId);

        NxPicklist pl = NxPicklist.builder()
                .tenantId(order.getTenantId())
                .name("PL-" + order.getExternalId() + "-" + LocalDateTime.now().toLocalDate())
                .waveType("SINGLE_ORDER")
                .priority("NORMAL")
                .status("OPEN")
                .totalItems(items.stream().mapToInt(NxOrderItem::getQuantity).sum())
                .pickedItems(0)
                .orderIds(orderId.toString())
                .build();
        pl = picklistRepository.save(pl);
        seedItemsIntoPicklist(pl, items);
        return pl;
    }

    @Transactional
    public NxPicklist seedPicklistItems(UUID picklistId, UUID orderId) {
        NxPicklist pl = getPicklist(picklistId);
        List<NxOrderItem> items = orderItemRepository.findByOrderId(orderId);
        seedItemsIntoPicklist(pl, items);
        pl.setTotalItems(picklistItemRepository.findByPicklistId(picklistId).size());
        pl.setOrderIds(orderId.toString());
        return picklistRepository.save(pl);
    }

    private void seedItemsIntoPicklist(NxPicklist pl, List<NxOrderItem> items) {
        for (NxOrderItem item : items) {
            boolean exists = picklistItemRepository.findByPicklistId(pl.getId()).stream()
                    .anyMatch(i -> item.getId() != null && item.getId().equals(i.getOrderItemId()));
            if (exists) continue;
            picklistItemRepository.save(NxPicklistItem.builder()
                    .picklistId(pl.getId())
                    .tenantId(pl.getTenantId())
                    .orderId(item.getOrderId())
                    .orderItemId(item.getId())
                    .sku(item.getSku())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .pickedQuantity(0)
                    .status("PENDING")
                    .build());
        }
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
