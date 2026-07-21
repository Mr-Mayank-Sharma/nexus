package com.nexus.oms.service;

import com.nexus.oms.entity.NxPicker;
import com.nexus.oms.entity.NxPickerAssignment;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PickerAssignmentRepository;
import com.nexus.oms.repository.PickerRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PickerService {

    private static final Logger log = LoggerFactory.getLogger(PickerService.class);

    private final PickerRepository pickerRepository;
    private final PickerAssignmentRepository pickerAssignmentRepository;

    public PickerService(PickerRepository pickerRepository, PickerAssignmentRepository pickerAssignmentRepository) {
        this.pickerRepository = pickerRepository;
        this.pickerAssignmentRepository = pickerAssignmentRepository;
    }

    // ─── Picker Management ─────────────────────────────────────────────────

    @Transactional
    public NxPicker createPicker(NxPicker picker) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        picker.setTenantId(tenantId);
        return pickerRepository.save(picker);
    }

    @Transactional
    public NxPicker updatePicker(UUID id, NxPicker updates) {
        NxPicker picker = pickerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Picker", id));
        if (updates.getName() != null) picker.setName(updates.getName());
        if (updates.getEmployeeId() != null) picker.setEmployeeId(updates.getEmployeeId());
        if (updates.getMaxConcurrentOrders() != null) picker.setMaxConcurrentOrders(updates.getMaxConcurrentOrders());
        if (updates.getActive() != null) picker.setActive(updates.getActive());
        return pickerRepository.save(picker);
    }

    @Transactional
    public NxPicker updateStatus(UUID id, String status) {
        NxPicker picker = pickerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Picker", id));
        picker.setStatus(status);
        picker.setLastActiveAt(LocalDateTime.now());
        return pickerRepository.save(picker);
    }

    public NxPicker getPicker(UUID id) {
        return pickerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Picker", id));
    }

    public List<NxPicker> getPickers(UUID nodeId) {
        return pickerRepository.findByNodeIdAndActiveTrue(nodeId);
    }

    public List<NxPicker> getAvailablePickers(UUID nodeId) {
        return pickerRepository.findAvailablePickers(nodeId);
    }

    // ─── FIFO Assignment ───────────────────────────────────────────────────

    @Transactional
    public NxPickerAssignment assignNextAvailable(UUID nodeId, UUID pickupOrderId, String orderNumber) {
        List<NxPicker> availablePickers = pickerRepository.findAvailablePickers(nodeId);
        if (availablePickers.isEmpty()) {
            throw new BadRequestException("No available pickers for this node");
        }

        NxPicker picker = availablePickers.get(0);

        NxPickerAssignment assignment = NxPickerAssignment.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .pickerId(picker.getId())
                .pickupOrderId(pickupOrderId)
                .orderNumber(orderNumber)
                .nodeId(nodeId)
                .status("ASSIGNED")
                .priority(10)
                .build();
        assignment = pickerAssignmentRepository.save(assignment);

        picker.setCurrentOrderId(pickupOrderId);
        picker.setStatus("PICKING");
        pickerRepository.save(picker);

        log.info("Assigned order {} to picker {} (FIFO)", orderNumber, picker.getName());
        return assignment;
    }

    @Transactional
    public NxPickerAssignment assignPicker(UUID pickerId, UUID pickupOrderId, String orderNumber, UUID nodeId) {
        NxPicker picker = pickerRepository.findById(pickerId)
                .orElseThrow(() -> new ResourceNotFoundException("Picker", pickerId));

        if (!"AVAILABLE".equals(picker.getStatus())) {
            throw new BadRequestException("Picker is not available");
        }

        long activeCount = pickerAssignmentRepository.findByPickerIdAndStatus(pickerId, "ASSIGNED").size()
                         + pickerAssignmentRepository.findByPickerIdAndStatus(pickerId, "IN_PROGRESS").size();

        if (activeCount >= picker.getMaxConcurrentOrders()) {
            throw new BadRequestException("Picker has reached maximum concurrent orders");
        }

        NxPickerAssignment assignment = NxPickerAssignment.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .pickerId(pickerId)
                .pickupOrderId(pickupOrderId)
                .orderNumber(orderNumber)
                .nodeId(nodeId)
                .status("ASSIGNED")
                .priority(10)
                .build();
        assignment = pickerAssignmentRepository.save(assignment);

        if (activeCount == 0) {
            picker.setStatus("PICKING");
        }
        picker.setCurrentOrderId(pickupOrderId);
        pickerRepository.save(picker);

        log.info("Assigned order {} to picker {}", orderNumber, picker.getName());
        return assignment;
    }

    @Transactional
    public NxPickerAssignment startAssignment(UUID assignmentId) {
        NxPickerAssignment assignment = pickerAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("PickerAssignment", assignmentId));

        assignment.setStatus("IN_PROGRESS");
        assignment.setStartedAt(LocalDateTime.now());
        return pickerAssignmentRepository.save(assignment);
    }

    @Transactional
    public NxPickerAssignment completeAssignment(UUID assignmentId) {
        NxPickerAssignment assignment = pickerAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("PickerAssignment", assignmentId));

        assignment.setStatus("COMPLETED");
        assignment.setCompletedAt(LocalDateTime.now());
        assignment = pickerAssignmentRepository.save(assignment);

        UUID pickerId = assignment.getPickerId();
        NxPicker picker = pickerRepository.findById(pickerId)
                .orElseThrow(() -> new ResourceNotFoundException("Picker", pickerId));

        picker.setOrdersCompletedToday(picker.getOrdersCompletedToday() + 1);
        picker.setCurrentOrderId(null);
        picker.setLastActiveAt(LocalDateTime.now());

        long remaining = pickerAssignmentRepository.findByPickerIdAndStatus(picker.getId(), "ASSIGNED").size()
                       + pickerAssignmentRepository.findByPickerIdAndStatus(picker.getId(), "IN_PROGRESS").size();

        if (remaining == 0) {
            picker.setStatus("AVAILABLE");
        }
        pickerRepository.save(picker);

        log.info("Completed assignment {} for picker {}", assignmentId, picker.getName());
        return assignment;
    }

    public List<NxPickerAssignment> getActiveAssignments(UUID nodeId) {
        return pickerAssignmentRepository.findActiveAssignmentsByNode(nodeId);
    }

    public List<NxPickerAssignment> getPickerAssignments(UUID pickerId) {
        return pickerAssignmentRepository.findActiveAssignmentsByPicker(pickerId);
    }

    public Map<String, Object> getPickerStats(UUID nodeId) {
        List<NxPicker> pickers = pickerRepository.findByNodeIdAndActiveTrue(nodeId);
        List<NxPickerAssignment> active = pickerAssignmentRepository.findActiveAssignmentsByNode(nodeId);

        long available = pickers.stream().filter(p -> "AVAILABLE".equals(p.getStatus())).count();
        long picking = pickers.stream().filter(p -> "PICKING".equals(p.getStatus())).count();
        int totalToday = pickers.stream().mapToInt(NxPicker::getOrdersCompletedToday).sum();
        int totalItems = pickers.stream().mapToInt(NxPicker::getItemsPickedToday).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPickers", pickers.size());
        stats.put("available", available);
        stats.put("picking", picking);
        stats.put("activeAssignments", active.size());
        stats.put("ordersCompletedToday", totalToday);
        stats.put("itemsPickedToday", totalItems);
        return stats;
    }
}
