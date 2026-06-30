package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Inventory", description = "Inventory management APIs")
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "List all inventory for current tenant")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxInventory>>> getInventory() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryByTenant(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Get inventory by SKU")
    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<NxInventory>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getBySku(TenantContext.getCurrentTenantId(), sku)));
    }

    @Operation(summary = "Adjust inventory quantity")
    @PutMapping("/adjust")
    public ResponseEntity<ApiResponse<NxInventory>> adjustInventory(@RequestBody Map<String, Object> request) {
        UUID id = UUID.fromString((String) request.get("id"));
        int quantityChange = (int) request.get("quantityChange");
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.adjustInventory(id, quantityChange), "Inventory adjusted"));
    }

    @Operation(summary = "Get available-to-promise quantity for SKU")
    @GetMapping("/atp")
    public ResponseEntity<ApiResponse<Integer>> getAtp(@RequestParam String sku) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getAvailableToPromise(TenantContext.getCurrentTenantId(), sku)));
    }
}
