package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxReplenishmentRule;
import com.nexus.oms.entity.NxReplenishmentSuggestion;
import com.nexus.oms.service.ReplenishmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Replenishment Rules", description = "Replenishment rules engine and suggestion management")
@RestController
@RequestMapping("/replenishment")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;

    public ReplenishmentController(ReplenishmentService replenishmentService) {
        this.replenishmentService = replenishmentService;
    }

    // ---- Rules ----

    @Operation(summary = "List active replenishment rules for a warehouse")
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<NxReplenishmentRule>>> getRules(@RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.getRules(warehouseId)));
    }

    @Operation(summary = "Get a replenishment rule by ID")
    @GetMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<NxReplenishmentRule>> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.getRule(id)));
    }

    @Operation(summary = "Create a new replenishment rule")
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<NxReplenishmentRule>> createRule(
            @RequestBody NxReplenishmentRule rule) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.createRule(rule), "Rule created"));
    }

    @Operation(summary = "Update a replenishment rule")
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<NxReplenishmentRule>> updateRule(
            @PathVariable UUID id,
            @RequestBody NxReplenishmentRule rule) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.updateRule(id, rule), "Rule updated"));
    }

    @Operation(summary = "Delete (deactivate) a replenishment rule")
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID id) {
        replenishmentService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Rule deactivated"));
    }

    // ---- Suggestions ----

    @Operation(summary = "List replenishment suggestions with optional status filter")
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<NxReplenishmentSuggestion>>> getSuggestions(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.getSuggestions(warehouseId, status)));
    }

    @Operation(summary = "Get a suggestion by ID")
    @GetMapping("/suggestions/{id}")
    public ResponseEntity<ApiResponse<NxReplenishmentSuggestion>> getSuggestion(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.getSuggestion(id)));
    }

    @Operation(summary = "Approve a pending replenishment suggestion")
    @PostMapping("/suggestions/{id}/approve")
    public ResponseEntity<ApiResponse<NxReplenishmentSuggestion>> approveSuggestion(
            @PathVariable UUID id,
            @RequestParam String approvedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                replenishmentService.approveSuggestion(id, approvedBy), "Suggestion approved"));
    }

    @Operation(summary = "Reject a pending replenishment suggestion")
    @PostMapping("/suggestions/{id}/reject")
    public ResponseEntity<ApiResponse<NxReplenishmentSuggestion>> rejectSuggestion(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success(
                replenishmentService.rejectSuggestion(id, reason), "Suggestion rejected"));
    }

    // ---- Auto-generate ----

    @Operation(summary = "Auto-generate replenishment suggestions based on rules and current inventory")
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<List<NxReplenishmentSuggestion>>> generateSuggestions(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(
                replenishmentService.generateSuggestions(warehouseId), "Suggestions generated"));
    }

    // ---- Stats ----

    @Operation(summary = "Get replenishment summary statistics")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(@RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(replenishmentService.getReplenishmentStats(warehouseId)));
    }
}
