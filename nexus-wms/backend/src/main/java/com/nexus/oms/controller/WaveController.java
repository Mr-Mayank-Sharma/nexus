package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxWave;
import com.nexus.oms.entity.NxWaveRule;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.WaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Wave Planning", description = "Wave planning and release management APIs")
@RestController
@RequestMapping("/waves")
public class WaveController {

    private final WaveService waveService;

    public WaveController(WaveService waveService) {
        this.waveService = waveService;
    }

    @Operation(summary = "List waves with optional status filter")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxWave>>> getWaves(
            @RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(waveService.getWaves(tenantId, status)));
    }

    @Operation(summary = "Get wave by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxWave>> getWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.getWave(id)));
    }

    @Operation(summary = "Create a new wave")
    @PostMapping
    public ResponseEntity<ApiResponse<NxWave>> createWave(@RequestBody NxWave wave) {
        wave.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(waveService.createWave(wave), "Wave created"));
    }

    @Operation(summary = "Update a DRAFT wave")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxWave>> updateWave(@PathVariable UUID id, @RequestBody NxWave wave) {
        return ResponseEntity.ok(ApiResponse.success(waveService.updateWave(id, wave), "Wave updated"));
    }

    @Operation(summary = "Add a rule to a DRAFT wave")
    @PostMapping("/{id}/rules")
    public ResponseEntity<ApiResponse<NxWaveRule>> addRule(@PathVariable UUID id, @RequestBody NxWaveRule rule) {
        return ResponseEntity.ok(ApiResponse.success(waveService.addRule(id, rule), "Rule added"));
    }

    @Operation(summary = "Remove a wave rule")
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<ApiResponse<String>> removeRule(@PathVariable UUID ruleId) {
        waveService.removeRule(ruleId);
        return ResponseEntity.ok(ApiResponse.success("Rule removed"));
    }

    @Operation(summary = "Plan a DRAFT wave")
    @PostMapping("/{id}/plan")
    public ResponseEntity<ApiResponse<NxWave>> planWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.planWave(id), "Wave planned"));
    }

    @Operation(summary = "Release a PLANNED wave")
    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<NxWave>> releaseWave(
            @PathVariable UUID id,
            @RequestParam(required = false) String releasedBy) {
        if (releasedBy == null || releasedBy.isBlank()) {
            releasedBy = TenantContext.getCurrentUsername();
        }
        return ResponseEntity.ok(ApiResponse.success(waveService.releaseWave(id, releasedBy), "Wave released"));
    }

    @Operation(summary = "Pause an IN_PROGRESS wave")
    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<NxWave>> pauseWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.pauseWave(id), "Wave paused"));
    }

    @Operation(summary = "Resume a RELEASING_PAUSED wave")
    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<NxWave>> resumeWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.resumeWave(id), "Wave resumed"));
    }

    @Operation(summary = "Complete an IN_PROGRESS wave")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<NxWave>> completeWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.completeWave(id), "Wave completed"));
    }

    @Operation(summary = "Cancel a wave from any active state")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<NxWave>> cancelWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.cancelWave(id), "Wave cancelled"));
    }

    @Operation(summary = "Run AI optimization on a wave")
    @PostMapping("/{id}/optimize")
    public ResponseEntity<ApiResponse<NxWave>> optimizeWave(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(waveService.optimizeWave(id), "Wave optimized"));
    }

    @Operation(summary = "Get wave statistics for the tenant")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWaveStats() {
        return ResponseEntity.ok(ApiResponse.success(waveService.getWaveStats(TenantContext.getCurrentTenantId())));
    }
}
