package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxAutomationAlert;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationLog;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.service.AutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Automation", description = "Automation system interfaces for warehouse equipment integration")
@RestController
@RequestMapping("/automation")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    // ==================== System Management ====================

    @Operation(summary = "List automation systems")
    @GetMapping("/systems")
    public ResponseEntity<ApiResponse<List<NxAutomationSystem>>> getSystems(
            @RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getSystems(warehouseId)));
    }

    @Operation(summary = "Get automation system by ID")
    @GetMapping("/systems/{id}")
    public ResponseEntity<ApiResponse<NxAutomationSystem>> getSystem(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getSystem(id)));
    }

    @Operation(summary = "Create a new automation system")
    @PostMapping("/systems")
    public ResponseEntity<ApiResponse<NxAutomationSystem>> createSystem(
            @RequestBody NxAutomationSystem system) {
        return ResponseEntity.ok(ApiResponse.success(automationService.createSystem(system), "Automation system created"));
    }

    @Operation(summary = "Update automation system details")
    @PutMapping("/systems/{id}")
    public ResponseEntity<ApiResponse<NxAutomationSystem>> updateSystem(
            @PathVariable UUID id,
            @RequestBody NxAutomationSystem system) {
        return ResponseEntity.ok(ApiResponse.success(automationService.updateSystem(id, system), "Automation system updated"));
    }

    @Operation(summary = "Toggle automation system active status")
    @PutMapping("/systems/{id}/toggle")
    public ResponseEntity<ApiResponse<NxAutomationSystem>> toggleSystem(
            @PathVariable UUID id,
            @RequestParam boolean isActive) {
        return ResponseEntity.ok(ApiResponse.success(automationService.toggleSystem(id, isActive), "System toggled"));
    }

    @Operation(summary = "Get aggregate health status for all systems in a warehouse")
    @GetMapping("/systems/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getSystemHealth(warehouseId)));
    }

    // ==================== Command Management ====================

    @Operation(summary = "Send a command to an automation system")
    @PostMapping("/commands")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> sendCommand(
            @RequestParam UUID systemId,
            @RequestBody NxAutomationCommand command) {
        return ResponseEntity.ok(ApiResponse.success(automationService.sendCommand(systemId, command), "Command sent"));
    }

    @Operation(summary = "List commands with optional filters")
    @GetMapping("/commands")
    public ResponseEntity<ApiResponse<List<NxAutomationCommand>>> getCommands(
            @RequestParam(required = false) UUID systemId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getCommands(systemId, status)));
    }

    @Operation(summary = "Get command details by ID")
    @GetMapping("/commands/{id}")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> getCommand(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getCommand(id)));
    }

    @Operation(summary = "Cancel a pending or sent command")
    @PostMapping("/commands/{id}/cancel")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> cancelCommand(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(automationService.cancelCommand(id), "Command cancelled"));
    }

    @Operation(summary = "Retry a failed command")
    @PostMapping("/commands/{id}/retry")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> retryCommand(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(automationService.retryCommand(id), "Command retried"));
    }

    @Operation(summary = "Get command statistics for a warehouse")
    @GetMapping("/commands/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommandStats(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getCommandStats(warehouseId)));
    }

    // ==================== Log Management ====================

    @Operation(summary = "Get automation logs with filters")
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<NxAutomationLog>>> getLogs(
            @RequestParam(required = false) UUID systemId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getLogs(systemId, level, from, to)));
    }

    @Operation(summary = "Create a log entry")
    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<NxAutomationLog>> addLog(
            @RequestParam UUID systemId,
            @RequestBody NxAutomationLog logData) {
        return ResponseEntity.ok(ApiResponse.success(automationService.addLog(systemId, logData), "Log created"));
    }

    @Operation(summary = "Get recent logs across all systems in a warehouse")
    @GetMapping("/logs/recent")
    public ResponseEntity<ApiResponse<List<NxAutomationLog>>> getRecentLogs(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getRecentLogs(warehouseId)));
    }

    // ==================== Alert Management ====================

    @Operation(summary = "Get automation alerts with optional filters")
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<NxAutomationAlert>>> getAlerts(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getAlerts(warehouseId, status)));
    }

    @Operation(summary = "Create an automation alert")
    @PostMapping("/alerts")
    public ResponseEntity<ApiResponse<NxAutomationAlert>> createAlert(
            @RequestBody NxAutomationAlert alert) {
        return ResponseEntity.ok(ApiResponse.success(automationService.createAlert(alert), "Alert created"));
    }

    @Operation(summary = "Acknowledge an alert")
    @PutMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<ApiResponse<NxAutomationAlert>> acknowledgeAlert(
            @PathVariable UUID id,
            @RequestParam String acknowledgedBy) {
        return ResponseEntity.ok(ApiResponse.success(automationService.acknowledgeAlert(id, acknowledgedBy), "Alert acknowledged"));
    }

    @Operation(summary = "Resolve an alert")
    @PutMapping("/alerts/{id}/resolve")
    public ResponseEntity<ApiResponse<NxAutomationAlert>> resolveAlert(
            @PathVariable UUID id,
            @RequestParam(required = false) String resolutionNotes) {
        return ResponseEntity.ok(ApiResponse.success(automationService.resolveAlert(id, resolutionNotes), "Alert resolved"));
    }

    @Operation(summary = "Get alert statistics for a warehouse")
    @GetMapping("/alerts/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAlertStats(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getAlertStats(warehouseId)));
    }

    // ==================== Integration Helpers ====================

    @Operation(summary = "Execute a pick command on an ASRS or robot system")
    @PostMapping("/integration/pick")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> executePick(
            @RequestParam UUID systemId,
            @RequestParam String binLocation,
            @RequestParam int quantity,
            @RequestParam String destination) {
        return ResponseEntity.ok(ApiResponse.success(
                automationService.executePick(systemId, binLocation, quantity, destination), "Pick command sent"));
    }

    @Operation(summary = "Execute a sort command on a sortation system")
    @PostMapping("/integration/sort")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> executeSort(
            @RequestParam UUID systemId,
            @RequestParam String packageId,
            @RequestParam String destinationChute) {
        return ResponseEntity.ok(ApiResponse.success(
                automationService.executeSort(systemId, packageId, destinationChute), "Sort command sent"));
    }

    @Operation(summary = "Execute a convey command on a conveyor system")
    @PostMapping("/integration/convey")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> executeConvey(
            @RequestParam UUID systemId,
            @RequestParam String packageId,
            @RequestParam String destinationZone) {
        return ResponseEntity.ok(ApiResponse.success(
                automationService.executeConvey(systemId, packageId, destinationZone), "Convey command sent"));
    }

    @Operation(summary = "Get current status of an automation system")
    @GetMapping("/integration/status")
    public ResponseEntity<ApiResponse<NxAutomationCommand>> getSystemStatus(
            @RequestParam UUID systemId) {
        return ResponseEntity.ok(ApiResponse.success(automationService.getStatus(systemId), "Status retrieved"));
    }
}
