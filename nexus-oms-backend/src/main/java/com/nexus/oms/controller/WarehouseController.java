package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.service.LocationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Locations", description = "Warehouses and distribution centres the OMS routes to")
@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

    private final LocationService warehouseService;

    public WarehouseController(LocationService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "List all warehouses")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Warehouse>>> getWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getAllWarehouses(pageable)));
    }

    @Operation(summary = "Get warehouse by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Warehouse>> getWarehouse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouse(id)));
    }

    @Operation(summary = "Create a new warehouse")
    @PostMapping
    public ResponseEntity<ApiResponse<Warehouse>> createWarehouse(@Valid @RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createWarehouse(warehouse), "Warehouse created"));
    }

    @Operation(summary = "Update warehouse details")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Warehouse>> updateWarehouse(@PathVariable UUID id, @Valid @RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateWarehouse(id, warehouse), "Warehouse updated"));
    }

    @Operation(summary = "Delete a warehouse")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable UUID id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Warehouse deleted"));
    }

    @Operation(summary = "Capacity summary for one location")
    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWarehouseSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouseSummary(id)));
    }

    @Operation(summary = "Get all warehouses summary for dashboard")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllWarehousesSummary() {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getAllWarehousesSummary()));
    }
}
