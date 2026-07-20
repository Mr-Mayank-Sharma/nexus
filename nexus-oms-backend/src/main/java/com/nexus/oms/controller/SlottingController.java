package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxSlottingAssignment;
import com.nexus.oms.entity.NxSlottingAudit;
import com.nexus.oms.entity.NxSlottingRule;
import com.nexus.oms.service.SlottingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Slotting", description = "Slotting optimization APIs")
@RestController
@RequestMapping("/slotting")
public class SlottingController {

    private final SlottingService slottingService;

    public SlottingController(SlottingService slottingService) {
        this.slottingService = slottingService;
    }

    @Operation(summary = "List all slotting assignments for a warehouse")
    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<NxSlottingAssignment>>> getAssignments(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.getAssignments(warehouseId)));
    }

    @Operation(summary = "Get a single slotting assignment by ID")
    @GetMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<NxSlottingAssignment>> getAssignment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.getAssignment(id)));
    }

    @Operation(summary = "List slotting rules for a warehouse, sorted by priority")
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<NxSlottingRule>>> getRules(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.getRules(warehouseId)));
    }

    @Operation(summary = "Create a new slotting rule")
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<NxSlottingRule>> createRule(@RequestBody NxSlottingRule rule) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.createRule(rule), "Slotting rule created"));
    }

    @Operation(summary = "Update an existing slotting rule")
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<NxSlottingRule>> updateRule(
            @PathVariable UUID id,
            @RequestBody NxSlottingRule rule) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.updateRule(id, rule), "Slotting rule updated"));
    }

    @Operation(summary = "Enable or disable a slotting rule")
    @PutMapping("/rules/{id}/toggle")
    public ResponseEntity<ApiResponse<NxSlottingRule>> toggleRule(
            @PathVariable UUID id,
            @RequestParam Boolean isActive) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.toggleRule(id, isActive),
                "Slotting rule " + (isActive ? "enabled" : "disabled")));
    }

    @Operation(summary = "Analyze current slotting efficiency for a warehouse")
    @GetMapping("/analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeSlotting(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.analyzeSlotting(warehouseId)));
    }

    @Operation(summary = "Run AI-driven slotting optimization for a warehouse")
    @PostMapping("/optimize")
    public ResponseEntity<ApiResponse<Map<String, Object>>> optimizeSlotting(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.optimizeSlotting(warehouseId),
                "Slotting optimization completed"));
    }

    @Operation(summary = "Manually reassign a SKU to a different bin")
    @PostMapping("/reassign")
    public ResponseEntity<ApiResponse<NxSlottingAssignment>> reassignSku(
            @RequestParam String sku,
            @RequestParam UUID warehouseId,
            @RequestParam UUID targetBinId,
            @RequestParam Integer quantity,
            @RequestParam(defaultValue = "system") String performedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                slottingService.reassignSku(sku, warehouseId, targetBinId, quantity, performedBy),
                "SKU reassigned successfully"));
    }

    @Operation(summary = "Get SKU velocity analysis for a warehouse")
    @GetMapping("/velocity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVelocityAnalysis(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.getVelocityAnalysis(warehouseId)));
    }

    @Operation(summary = "Get space utilization breakdown for a warehouse")
    @GetMapping("/space")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSpaceUtilization(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.getSpaceUtilization(warehouseId)));
    }

    @Operation(summary = "Get slotting audit log for a warehouse")
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<NxSlottingAudit>>> getSlottingAuditLog(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(slottingService.getSlottingAuditLog(warehouseId, from, to)));
    }
}
