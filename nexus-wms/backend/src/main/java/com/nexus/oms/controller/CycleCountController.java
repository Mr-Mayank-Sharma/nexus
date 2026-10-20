package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.CycleCountRequest;
import com.nexus.oms.entity.NxCycleCount;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.CycleCountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cycle-counts")
public class CycleCountController {

    private final CycleCountService cycleCountService;

    public CycleCountController(CycleCountService cycleCountService) {
        this.cycleCountService = cycleCountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NxCycleCount>>> getCycleCounts(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                cycleCountService.getCycleCounts(TenantContext.getCurrentTenantId(), status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCycleCount>> getCycleCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(cycleCountService.getCycleCount(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxCycleCount>> createCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cycleCountService.createCycleCount(TenantContext.getCurrentTenantId(), request),
                "Cycle count created"));
    }

    @PostMapping("/{id}/count")
    public ResponseEntity<ApiResponse<NxCycleCount>> performCount(
            @PathVariable UUID id,
            @RequestParam Integer countedQty,
            @RequestParam(defaultValue = "system") String countedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                cycleCountService.performCount(id, countedQty, countedBy),
                "Cycle count recorded"));
    }
}
