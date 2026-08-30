package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.SyncConflictResolutionRequest;
import com.nexus.oms.dto.SyncFieldMappingRequest;
import com.nexus.oms.dto.SyncReconciliationReport;
import com.nexus.oms.entity.NxSyncConflict;
import com.nexus.oms.entity.NxSyncFieldMapping;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.SyncConflictService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sync")
public class SyncController {

    private final SyncConflictService syncConflictService;

    public SyncController(SyncConflictService syncConflictService) {
        this.syncConflictService = syncConflictService;
    }

    @GetMapping("/conflicts")
    public ResponseEntity<ApiResponse<Page<NxSyncConflict>>> getConflicts(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                syncConflictService.getConflicts(TenantContext.getCurrentTenantId(), status, pageable)));
    }

    @GetMapping("/conflicts/{id}")
    public ResponseEntity<ApiResponse<NxSyncConflict>> getConflict(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(syncConflictService.getConflict(id)));
    }

    @PostMapping("/conflicts/{id}/resolve")
    public ResponseEntity<ApiResponse<NxSyncConflict>> resolveConflict(
            @PathVariable UUID id,
            @RequestBody(required = false) SyncConflictResolutionRequest request) {
        String resolution = request == null ? "LOCAL_WINS" : request.getResolution();
        return ResponseEntity.ok(ApiResponse.success(
                syncConflictService.resolveConflict(id, resolution,
                        request == null ? null : request.getMergedFields()),
                "Conflict resolved"));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<SyncReconciliationReport>> getReconciliation() {
        return ResponseEntity.ok(ApiResponse.success(
                syncConflictService.getReconciliationReport(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/field-mappings")
    public ResponseEntity<ApiResponse<List<NxSyncFieldMapping>>> getFieldMappings() {
        return ResponseEntity.ok(ApiResponse.success(
                syncConflictService.getFieldMappings(TenantContext.getCurrentTenantId())));
    }

    @PostMapping("/field-mappings")
    public ResponseEntity<ApiResponse<NxSyncFieldMapping>> upsertFieldMapping(
            @RequestBody SyncFieldMappingRequest request) {
        NxSyncFieldMapping mapping = NxSyncFieldMapping.builder()
                .entityType(request.getEntityType())
                .fieldName(request.getFieldName())
                .direction(request.getDirection())
                .winner(request.getWinner())
                .isActive(request.getIsActive() == null ? true : request.getIsActive())
                .build();
        return ResponseEntity.ok(ApiResponse.success(
                syncConflictService.upsertFieldMapping(mapping),
                "Field mapping saved"));
    }

    @PostMapping("/recovery")
    public ResponseEntity<ApiResponse<Integer>> triggerRecovery() {
        return ResponseEntity.ok(ApiResponse.success(
                syncConflictService.triggerRecovery(),
                "Recovery triggered"));
    }
}
