package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.*;
import com.nexus.oms.service.LaborService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Labor Management", description = "Warehouse labor tracking, scheduling, and efficiency APIs")
@RestController
@RequestMapping("/labor")
public class LaborController {

    private final LaborService laborService;

    public LaborController(LaborService laborService) {
        this.laborService = laborService;
    }

    @Operation(summary = "Clock in a worker")
    @PostMapping("/clock-in")
    public ResponseEntity<ApiResponse<NxLaborEntry>> clockIn(
            @RequestParam UUID staffId,
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.clockIn(staffId, warehouseId), "Worker clocked in"));
    }

    @Operation(summary = "Clock out a worker")
    @PostMapping("/{id}/clock-out")
    public ResponseEntity<ApiResponse<NxLaborEntry>> clockOut(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.clockOut(id), "Worker clocked out"));
    }

    @Operation(summary = "Start break for a worker")
    @PostMapping("/{id}/break/start")
    public ResponseEntity<ApiResponse<NxLaborEntry>> startBreak(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.startBreak(id), "Break started"));
    }

    @Operation(summary = "End break for a worker")
    @PostMapping("/{id}/break/end")
    public ResponseEntity<ApiResponse<NxLaborEntry>> endBreak(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.endBreak(id), "Break ended"));
    }

    @Operation(summary = "Update worker progress metrics")
    @PutMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<NxLaborEntry>> updateProgress(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> progressData) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.updateProgress(id, progressData), "Progress updated"));
    }

    @Operation(summary = "Assign a task to a worker")
    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<NxLaborEntry>> assignTask(
            @PathVariable UUID id,
            @RequestParam String taskType,
            @RequestParam(required = false) UUID waveId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.assignTask(id, taskType, waveId), "Task assigned"));
    }

    @Operation(summary = "Get all active workers in a warehouse")
    @GetMapping("/workers/active")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActiveWorkers(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getActiveWorkers(warehouseId)));
    }

    @Operation(summary = "Get labor statistics for a warehouse and date")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLaborStats(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getLaborStats(warehouseId, date)));
    }

    @Operation(summary = "Get efficiency breakdown by worker")
    @GetMapping("/efficiency/by-worker")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEfficiencyByWorker(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getEfficiencyByWorker(warehouseId, date)));
    }

    @Operation(summary = "Get efficiency breakdown by shift")
    @GetMapping("/efficiency/by-shift")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEfficiencyByShift(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getEfficiencyByShift(warehouseId, date)));
    }

    @Operation(summary = "Get efficiency breakdown by task type")
    @GetMapping("/efficiency/by-task")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEfficiencyByTaskType(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getEfficiencyByTaskType(warehouseId, date)));
    }

    @Operation(summary = "Get shift schedules for a warehouse and date")
    @GetMapping("/schedules")
    public ResponseEntity<ApiResponse<List<NxShiftSchedule>>> getShiftSchedules(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getShiftSchedules(warehouseId, date)));
    }

    @Operation(summary = "Create a shift schedule entry")
    @PostMapping("/schedules")
    public ResponseEntity<ApiResponse<NxShiftSchedule>> createShiftSchedule(
            @RequestBody NxShiftSchedule schedule) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.createShiftSchedule(schedule), "Shift schedule created"));
    }

    @Operation(summary = "Get active engineered standards for a warehouse")
    @GetMapping("/standards")
    public ResponseEntity<ApiResponse<List<NxEngineeredStandard>>> getEngineeredStandards(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getEngineeredStandards(warehouseId)));
    }

    @Operation(summary = "Create an engineered standard")
    @PostMapping("/standards")
    public ResponseEntity<ApiResponse<NxEngineeredStandard>> createEngineeredStandard(
            @RequestBody NxEngineeredStandard standard) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.createEngineeredStandard(standard), "Engineered standard created"));
    }

    @Operation(summary = "Calculate incentive pay for a worker")
    @GetMapping("/{id}/incentive")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateIncentivePay(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.calculateIncentivePay(id)));
    }

    @Operation(summary = "Get workload rules for a warehouse")
    @GetMapping("/workload-rules")
    public ResponseEntity<ApiResponse<List<NxWorkloadRule>>> getWorkloadRules(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getWorkloadRules(warehouseId)));
    }

    @Operation(summary = "Create a workload rule")
    @PostMapping("/workload-rules")
    public ResponseEntity<ApiResponse<NxWorkloadRule>> createWorkloadRule(
            @RequestBody NxWorkloadRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.createWorkloadRule(rule), "Workload rule created"));
    }

    @Operation(summary = "Update a workload rule")
    @PutMapping("/workload-rules/{id}")
    public ResponseEntity<ApiResponse<NxWorkloadRule>> updateWorkloadRule(
            @PathVariable UUID id,
            @RequestBody NxWorkloadRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.updateWorkloadRule(id, rule), "Workload rule updated"));
    }

    @Operation(summary = "Get current workload balance for a warehouse")
    @GetMapping("/workload/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkloadBalance(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getWorkloadBalance(warehouseId)));
    }

    @Operation(summary = "Get workload rebalancing recommendations")
    @GetMapping("/workload/rebalance")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> rebalanceWorkload(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.rebalanceWorkload(warehouseId)));
    }

    @Operation(summary = "Calculate performance vs engineered standards")
    @GetMapping("/performance/vs-standard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculatePerformanceVsStandard(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.calculatePerformanceVsStandard(warehouseId, date)));
    }

    @Operation(summary = "Log a productivity entry")
    @PostMapping("/productivity")
    public ResponseEntity<ApiResponse<NxProductivityLog>> logProductivity(
            @RequestBody NxProductivityLog log) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.logProductivity(log), "Productivity logged"));
    }

    @Operation(summary = "Get productivity logs for a warehouse")
    @GetMapping("/productivity")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProductivityLogs(
            @RequestParam UUID warehouseId,
            @RequestParam(defaultValue = "7") int daysBack) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getProductivityLogs(warehouseId, daysBack)));
    }

    @Operation(summary = "Get productivity breakdown by task type")
    @GetMapping("/productivity/by-task")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProductivityByTaskType(
            @RequestParam UUID warehouseId,
            @RequestParam(defaultValue = "7") int daysBack) {
        return ResponseEntity.ok(ApiResponse.success(
                laborService.getProductivityByTaskType(warehouseId, daysBack)));
    }
}
