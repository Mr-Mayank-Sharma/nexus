package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.InventoryLedger;
import com.nexus.oms.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag(name = "Inventory", description = "Inventory management APIs")
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "List all inventory for current tenant")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxInventory>>> getInventory() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryByTenant(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Create a new inventory record")
    @PostMapping
    public ResponseEntity<ApiResponse<NxInventory>> createInventory(@RequestBody NxInventory inventory) {
        inventory.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(inventoryService.createInventory(inventory), "Inventory created"));
    }

    @Operation(summary = "Get inventory by SKU")
    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<NxInventory>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getBySku(TenantContext.getCurrentTenantId(), sku)));
    }

    @Operation(summary = "Adjust inventory quantity by SKU")
    @PostMapping("/adjust")
    public ResponseEntity<ApiResponse<NxInventory>> adjustInventory(@RequestBody Map<String, Object> request) {
        String sku = (String) request.get("sku");
        int quantity = ((Number) request.getOrDefault("quantity", 0)).intValue();
        Object node = request.get("nodeId");
        UUID nodeId = node == null || String.valueOf(node).isBlank() ? null : UUID.fromString(String.valueOf(node));
        String reason = request.get("reason") == null ? null : String.valueOf(request.get("reason"));
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Adjusting inventory SKU={} node={} by quantity delta={}", sku, nodeId, quantity);
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.adjustInventoryBySku(tenantId, sku, nodeId, quantity, reason), "Inventory adjusted"));
    }

    @Operation(summary = "Compare every stock row with the ledger; non-zero 'unexplained' means unrecorded change")
    @GetMapping("/ledger-check")
    public ResponseEntity<ApiResponse<List<InventoryLedger.RowCheck>>> ledgerCheck() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.ledgerCheck(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Write opening-balance ledger lines for stock that predates the ledger")
    @PostMapping("/ledger/baseline")
    public ResponseEntity<ApiResponse<Integer>> baselineLedger() {
        int rows = inventoryService.baselineLedger(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(rows, rows + " stock row(s) given an opening balance"));
    }

    @Operation(summary = "Inventory ledger: every change to on-hand, allocated and reserved stock")
    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<com.nexus.oms.entity.NxInventoryMovement>>> movements(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.movements(TenantContext.getCurrentTenantId(), sku, nodeId, limit)));
    }

    @Operation(summary = "Get available-to-promise quantity for SKU")
    @GetMapping("/atp")
    public ResponseEntity<ApiResponse<Integer>> getAtp(@RequestParam String sku) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getAvailableToPromise(TenantContext.getCurrentTenantId(), sku)));
    }
}
