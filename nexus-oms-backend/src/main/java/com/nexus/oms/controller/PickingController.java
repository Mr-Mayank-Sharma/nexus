package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.service.PickingService;
import com.nexus.oms.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Picking", description = "Picking management APIs")
@RestController
@RequestMapping("/picking")
public class PickingController {

    private final PickingService pickingService;

    public PickingController(PickingService pickingService) {
        this.pickingService = pickingService;
    }

    @Operation(summary = "List all picklists")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxPicklist>>> getAllPicklists() {
        return ResponseEntity.ok(ApiResponse.success(
                pickingService.getPicklists(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "List all picklists with optional status filter")
    @GetMapping("/picklists")
    public ResponseEntity<ApiResponse<List<NxPicklist>>> getPicklists(@RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxPicklist> result = status != null
                ? pickingService.getPicklistsByStatus(tenantId, status)
                : pickingService.getPicklists(tenantId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Get picklist by ID")
    @GetMapping("/picklists/{id}")
    public ResponseEntity<ApiResponse<NxPicklist>> getPicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getPicklist(id)));
    }

    @Operation(summary = "Get picklist items")
    @GetMapping("/picklists/{id}/items")
    public ResponseEntity<ApiResponse<List<NxPicklistItem>>> getPicklistItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getPicklistItems(id)));
    }

    @Operation(summary = "Create a new picklist")
    @PostMapping("/picklists")
    public ResponseEntity<ApiResponse<NxPicklist>> createPicklist(@Valid @RequestBody NxPicklist picklist) {
        picklist.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(pickingService.createPicklist(picklist), "Picklist created"));
    }

    @Operation(summary = "Start picking a picklist")
    @PostMapping("/picklists/{id}/assign")
    public ResponseEntity<ApiResponse<NxPicklist>> assignPicker(@PathVariable UUID id, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.assignPicker(id, staffId), "Picker assigned"));
    }

    @Operation(summary = "Mark an item as picked")
    @PostMapping("/items/{id}/pick")
    public ResponseEntity<ApiResponse<NxPicklistItem>> pickItem(@PathVariable UUID id, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.pickItem(id, staffId), "Item picked"));
    }

    @Operation(summary = "Complete a picklist")
    @PostMapping("/picklists/{id}/complete")
    public ResponseEntity<ApiResponse<NxPicklist>> completePicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.completePicklist(id), "Picklist completed"));
    }

    @Operation(summary = "Cancel a picklist")
    @PostMapping("/picklists/{id}/cancel")
    public ResponseEntity<ApiResponse<NxPicklist>> cancelPicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.cancelPicklist(id), "Picklist cancelled"));
    }

    @Operation(summary = "Get picking KPIs")
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getDashboardKPIs(TenantContext.getCurrentTenantId())));
    }
}
