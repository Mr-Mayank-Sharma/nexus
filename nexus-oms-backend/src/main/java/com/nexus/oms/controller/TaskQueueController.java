package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPickerAssignment;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.PicklistItemRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.PickerAssignmentRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/task-queues")
public class TaskQueueController {

    private static final Set<String> PICK_TASK_STATUSES = Set.of("OPEN", "IN_PROGRESS");
    private static final Set<String> ACTIVE_COMMAND_STATUSES = Set.of("PENDING", "SENT", "EXECUTING", "ACKNOWLEDGED");

    private final PicklistRepository picklistRepository;
    private final PicklistItemRepository picklistItemRepository;
    private final AutomationCommandRepository commandRepository;
    private final PickerAssignmentRepository pickerAssignmentRepository;

    public TaskQueueController(PicklistRepository picklistRepository,
                               PicklistItemRepository picklistItemRepository,
                               AutomationCommandRepository commandRepository,
                               PickerAssignmentRepository pickerAssignmentRepository) {
        this.picklistRepository = picklistRepository;
        this.picklistItemRepository = picklistItemRepository;
        this.commandRepository = commandRepository;
        this.pickerAssignmentRepository = pickerAssignmentRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllTaskQueues(
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) UUID pickerId) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        List<Map<String, Object>> pickTasks = picklistRepository.findByTenantId(tenantId).stream()
                .filter(pl -> PICK_TASK_STATUSES.contains(pl.getStatus()))
                .map(pl -> {
                    long pending = picklistItemRepository.countByPicklistIdAndStatus(pl.getId(), "PENDING");
                    Map<String, Object> task = new LinkedHashMap<>();
                    task.put("id", pl.getId());
                    task.put("type", "PICK_TASK");
                    task.put("source", "WAVE");
                    task.put("status", pl.getStatus());
                    task.put("priority", pl.getPriority());
                    task.put("name", pl.getName());
                    task.put("totalItems", pl.getTotalItems());
                    task.put("pickedItems", pl.getPickedItems());
                    task.put("pendingItems", pending);
                    task.put("assigneeId", pl.getAssigneeId());
                    task.put("orderIds", pl.getOrderIds());
                    task.put("startedAt", pl.getStartedAt());
                    task.put("createdAt", pl.getCreatedAt());
                    return task;
                })
                .sorted(Comparator.comparingInt((Map<String, Object> t) -> priorityRank((String) t.get("priority")))
                        .thenComparing(t -> (java.time.LocalDateTime) t.get("createdAt")))
                .collect(Collectors.toList());

        List<Map<String, Object>> automationTasks = commandRepository.findByTenantId(tenantId).stream()
                .filter(c -> ACTIVE_COMMAND_STATUSES.contains(c.getStatus()))
                .map(c -> {
                    Map<String, Object> task = new LinkedHashMap<>();
                    task.put("id", c.getId());
                    task.put("type", "AUTOMATION_COMMAND");
                    task.put("source", "WES");
                    task.put("status", c.getStatus());
                    task.put("priority", c.getPriority());
                    task.put("name", c.getCommandType());
                    task.put("systemId", c.getSystemId());
                    task.put("parameters", c.getParameters());
                    task.put("orderId", c.getOrderId());
                    task.put("picklistId", c.getPicklistId());
                    task.put("waveId", c.getWaveId());
                    task.put("retryCount", c.getRetryCount());
                    task.put("createdAt", c.getCreatedAt());
                    return task;
                })
                .sorted(Comparator.comparingInt((Map<String, Object> t) -> (Integer) t.getOrDefault("priority", 0)).reversed())
                .collect(Collectors.toList());

        List<NxPickerAssignment> assignments;
        if (nodeId != null) {
            assignments = pickerAssignmentRepository.findActiveAssignmentsByNode(nodeId);
        } else if (pickerId != null) {
            assignments = pickerAssignmentRepository.findActiveAssignmentsByPicker(pickerId);
        } else {
            assignments = List.of();
        }
        List<Map<String, Object>> pickupTasks = assignments.stream()
                .map(a -> {
                    Map<String, Object> task = new LinkedHashMap<>();
                    task.put("id", a.getId());
                    task.put("type", "PICKUP_TASK");
                    task.put("source", "BOPIS");
                    task.put("status", a.getStatus());
                    task.put("priority", a.getPriority());
                    task.put("name", a.getOrderNumber());
                    task.put("pickerId", a.getPickerId());
                    task.put("nodeId", a.getNodeId());
                    task.put("assignedAt", a.getAssignedAt());
                    task.put("createdAt", a.getCreatedAt());
                    return task;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pickTasks", pickTasks);
        result.put("automationTasks", automationTasks);
        result.put("pickupTasks", pickupTasks);
        result.put("total", pickTasks.size() + automationTasks.size() + pickupTasks.size());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTaskQueue(
            @PathVariable UUID id, @RequestBody Map<String, Object> request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String status = request.get("status") instanceof String s ? s : null;
        if (status == null || status.isBlank()) {
            Map<String, Object> invalid = new LinkedHashMap<>();
            invalid.put("id", id.toString());
            invalid.put("error", "status is required");
            return ResponseEntity.badRequest().body(ApiResponse.error("status is required"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id.toString());
        result.put("type", "UNKNOWN");

        NxPicklist picklist = picklistRepository.findById(id).orElse(null);
        if (picklist != null && picklist.getTenantId().equals(tenantId)) {
            picklist.setStatus(validateTransition(status, Set.of("OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"), "pick task"));
            if ("IN_PROGRESS".equals(status) && picklist.getStartedAt() == null) {
                picklist.setStartedAt(LocalDateTime.now());
            }
            if ("COMPLETED".equals(status)) {
                picklist.setCompletedAt(LocalDateTime.now());
                if (picklist.getPickedItems() == null) {
                    picklist.setPickedItems(picklist.getTotalItems());
                }
            }
            NxPicklist saved = picklistRepository.save(picklist);
            result.put("type", "PICK_TASK");
            result.put("source", "WAVE");
            result.put("status", saved.getStatus());
            return ResponseEntity.ok(ApiResponse.success(result, "Task queue updated"));
        }

        NxAutomationCommand command = commandRepository.findById(id).orElse(null);
        if (command != null && command.getTenantId().equals(tenantId)) {
            command.setStatus(validateTransition(status,
                    Set.of("PENDING", "SENT", "EXECUTING", "ACKNOWLEDGED", "COMPLETED", "FAILED", "CANCELLED"),
                    "automation command"));
            if ("SENT".equals(status)) {
                command.setSentAt(LocalDateTime.now());
            }
            if ("ACKNOWLEDGED".equals(status)) {
                command.setAcknowledgedAt(LocalDateTime.now());
            }
            if ("COMPLETED".equals(status)) {
                command.setCompletedAt(LocalDateTime.now());
                if (command.getSentAt() != null) {
                    command.setExecutionTimeMs(java.time.Duration.between(command.getSentAt(), LocalDateTime.now()).toMillis());
                }
            }
            if ("FAILED".equals(status)) {
                int retries = command.getRetryCount() != null ? command.getRetryCount() : 0;
                int maxRetries = command.getMaxRetries() != null ? command.getMaxRetries() : 3;
                command.setRetryCount(retries + 1);
                command.setStatus(retries + 1 <= maxRetries ? "PENDING" : "FAILED");
                command.setErrorMessage((String) request.getOrDefault("errorMessage", "Command failed"));
            }
            NxAutomationCommand saved = commandRepository.save(command);
            result.put("type", "AUTOMATION_COMMAND");
            result.put("source", "WES");
            result.put("status", saved.getStatus());
            result.put("retryCount", saved.getRetryCount());
            return ResponseEntity.ok(ApiResponse.success(result, "Task queue updated"));
        }

        NxPickerAssignment assignment = pickerAssignmentRepository.findById(id).orElse(null);
        if (assignment != null && assignment.getTenantId().equals(tenantId)) {
            assignment.setStatus(validateTransition(status,
                    Set.of("ASSIGNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"), "pickup task"));
            if ("IN_PROGRESS".equals(status)) {
                assignment.setStartedAt(LocalDateTime.now());
            }
            if ("COMPLETED".equals(status)) {
                assignment.setCompletedAt(LocalDateTime.now());
            }
            NxPickerAssignment saved = pickerAssignmentRepository.save(assignment);
            result.put("type", "PICKUP_TASK");
            result.put("source", "BOPIS");
            result.put("status", saved.getStatus());
            return ResponseEntity.ok(ApiResponse.success(result, "Task queue updated"));
        }

        throw new ResourceNotFoundException("Task", id);
    }

    private String validateTransition(String status, Set<String> allowed, String taskType) {
        String upper = status.toUpperCase();
        if (!allowed.contains(upper)) {
            throw new IllegalArgumentException("Invalid " + taskType + " status: " + status
                    + " (allowed: " + allowed + ")");
        }
        return upper;
    }

    private int priorityRank(String priority) {
        if (priority == null) return 3;
        return switch (priority.toUpperCase()) {
            case "CRITICAL", "URGENT" -> 0;
            case "HIGH" -> 1;
            case "NORMAL", "MEDIUM" -> 2;
            case "LOW" -> 4;
            default -> 3;
        };
    }
}
