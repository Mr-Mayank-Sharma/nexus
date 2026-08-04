package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.entity.NxUser;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.repository.UserRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
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
    private final UserRepository userRepository;
    private final WarehouseStaffRepository staffRepository;

    public PickingController(PickingService pickingService,
                             UserRepository userRepository,
                             WarehouseStaffRepository staffRepository) {
        this.pickingService = pickingService;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
    }

    @Operation(summary = "Resolve current user's staff profile")
    @GetMapping("/user-staff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserStaff(@RequestParam String username) {
        NxUser user = userRepository.findByUsername(username).orElse(null);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("staffId", null);
        data.put("role", null);
        if (user != null) {
            WarehouseStaff staff = staffRepository.findByUserId(user.getId()).orElse(null);
            if (staff != null) {
                data.put("staffId", staff.getId());
                data.put("role", staff.getRole());
            }
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @Operation(summary = "List all picklists")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxPicklist>>> getAllPicklists() {
        return ResponseEntity.ok(ApiResponse.success(
                pickingService.getPicklists(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "List all picklists (alias used by frontend)")
    @GetMapping("/lists")
    public ResponseEntity<ApiResponse<List<NxPicklist>>> listPicklists(@RequestParam(required = false) String status) {
        return getPicklists(status);
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
    @GetMapping({"/picklists/{id}", "/lists/{id}"})
    public ResponseEntity<ApiResponse<NxPicklist>> getPicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getPicklist(id)));
    }

    @Operation(summary = "Get picklist items")
    @GetMapping({"/picklists/{id}/items", "/lists/{id}/items"})
    public ResponseEntity<ApiResponse<List<NxPicklistItem>>> getPicklistItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getPicklistItems(id)));
    }

    @Operation(summary = "Create a new picklist")
    @PostMapping({"/picklists", "/lists"})
    public ResponseEntity<ApiResponse<NxPicklist>> createPicklist(@Valid @RequestBody NxPicklist picklist) {
        picklist.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(pickingService.createPicklist(picklist), "Picklist created"));
    }

    @Operation(summary = "Create a picklist from an order (auto-generates items)")
    @PostMapping({"/picklists/from-order", "/lists/from-order"})
    public ResponseEntity<ApiResponse<NxPicklist>> createPicklistFromOrder(@RequestParam UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                pickingService.createPicklistFromOrder(orderId), "Picklist created from order"));
    }

    @Operation(summary = "Seed picklist items from an order")
    @PostMapping("/seed-items")
    public ResponseEntity<ApiResponse<NxPicklist>> seedItems(@RequestBody Map<String, UUID> body) {
        return ResponseEntity.ok(ApiResponse.success(
                pickingService.seedPicklistItems(body.get("picklistId"), body.get("orderId")), "Picklist items seeded"));
    }

    @Operation(summary = "Start picking a picklist")
    @PostMapping({"/picklists/{id}/assign", "/lists/{id}/assign"})
    public ResponseEntity<ApiResponse<NxPicklist>> assignPicker(@PathVariable UUID id, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.assignPicker(id, staffId), "Picker assigned"));
    }

    @Operation(summary = "Mark an item as picked")
    @PostMapping("/items/{id}/pick")
    public ResponseEntity<ApiResponse<NxPicklistItem>> pickItem(@PathVariable UUID id, @RequestParam UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.pickItem(id, staffId), "Item picked"));
    }

    @Operation(summary = "Complete a picklist")
    @PostMapping({"/picklists/{id}/complete", "/lists/{id}/complete"})
    public ResponseEntity<ApiResponse<NxPicklist>> completePicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.completePicklist(id), "Picklist completed"));
    }

    @Operation(summary = "Cancel a picklist")
    @PostMapping({"/picklists/{id}/cancel", "/lists/{id}/cancel"})
    public ResponseEntity<ApiResponse<NxPicklist>> cancelPicklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(pickingService.cancelPicklist(id), "Picklist cancelled"));
    }

    @Operation(summary = "Get picking KPIs")
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(pickingService.getDashboardKPIs(TenantContext.getCurrentTenantId())));
    }
}
