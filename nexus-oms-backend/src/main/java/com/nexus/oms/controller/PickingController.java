package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.service.PickingService;
import com.nexus.oms.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/picking")
public class PickingController {

    private final PickingService pickingService;

    public PickingController(PickingService pickingService) {
        this.pickingService = pickingService;
    }

    @GetMapping("/picklists")
    public ResponseEntity<ApiResponse<List<NxPicklist>>> getPicklists(@RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxPicklist> result = status != null
                ? pickingService.getPicklistsByStatus(tenantId, status)
                : pickingService.getPicklists(tenantId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/picklists/{id}")
    public ResponseEntity<ApiResponse<NxPicklist>> getPicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getPicklist(id)));
    }

    @GetMapping("/picklists/{id}/items")
    public ResponseEntity<ApiResponse<List<NxPicklistItem>>> getPicklistItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getPicklistItems(id)));
    }

    @PostMapping("/picklists")
    public ResponseEntity<ApiResponse<NxPicklist>> createPicklist(@RequestBody NxPicklist picklist) {
        picklist.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(pickingService.createPicklist(picklist), "Picklist created"));
    }

    @PostMapping("/picklists/{id}/assign")
    public ResponseEntity<ApiResponse<NxPicklist>> assignPicker(@PathVariable UUID id, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.assignPicker(id, staffId), "Picker assigned"));
    }

    @PostMapping("/items/{id}/pick")
    public ResponseEntity<ApiResponse<NxPicklistItem>> pickItem(@PathVariable UUID id, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.pickItem(id, staffId), "Item picked"));
    }

    @PostMapping("/picklists/{id}/complete")
    public ResponseEntity<ApiResponse<NxPicklist>> completePicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.completePicklist(id), "Picklist completed"));
    }

    @PostMapping("/picklists/{id}/cancel")
    public ResponseEntity<ApiResponse<NxPicklist>> cancelPicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.cancelPicklist(id), "Picklist cancelled"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getDashboardKPIs(TenantContext.getCurrentTenantId())));
    }
}
