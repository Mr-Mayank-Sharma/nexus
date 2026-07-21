package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.entity.WarehouseBin;
import com.nexus.oms.entity.WarehouseEquipment;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.entity.WarehouseZone;
import com.nexus.oms.service.WarehouseService;
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

@Tag(name = "Warehouse", description = "Warehouse management APIs")
@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
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

    @Operation(summary = "Get zones for a warehouse")
    @GetMapping("/{id}/zones")
    public ResponseEntity<ApiResponse<List<WarehouseZone>>> getZones(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getZones(id)));
    }

    @Operation(summary = "Create a zone within a warehouse")
    @PostMapping("/zones")
    public ResponseEntity<ApiResponse<WarehouseZone>> createZone(@Valid @RequestBody WarehouseZone zone) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createZone(zone), "Zone created"));
    }

    @Operation(summary = "Update zone details")
    @PutMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<WarehouseZone>> updateZone(@PathVariable UUID id, @Valid @RequestBody WarehouseZone zone) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateZone(id, zone), "Zone updated"));
    }

    @Operation(summary = "Get bins for a warehouse")
    @GetMapping("/{id}/bins")
    public ResponseEntity<ApiResponse<List<WarehouseBin>>> getBins(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getBins(id)));
    }

    @Operation(summary = "Get empty bins for a warehouse")
    @GetMapping("/{id}/bins/empty")
    public ResponseEntity<ApiResponse<List<WarehouseBin>>> getEmptyBins(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getEmptyBins(id)));
    }

    @Operation(summary = "Create a bin location")
    @PostMapping("/bins")
    public ResponseEntity<ApiResponse<WarehouseBin>> createBin(@Valid @RequestBody WarehouseBin bin) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createBin(bin), "Bin created"));
    }

    @Operation(summary = "Reserve a bin")
    @PutMapping("/bins/{id}/reserve")
    public ResponseEntity<ApiResponse<WarehouseBin>> reserveBin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.reserveBin(id), "Bin reserved"));
    }

    @Operation(summary = "Release a bin")
    @PutMapping("/bins/{id}/release")
    public ResponseEntity<ApiResponse<WarehouseBin>> releaseBin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.releaseBin(id), "Bin released"));
    }

    @Operation(summary = "List warehouse staff")
    @GetMapping("/{id}/staff")
    public ResponseEntity<ApiResponse<List<WarehouseStaff>>> getStaff(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getStaff(id)));
    }

    @Operation(summary = "Assign staff to warehouse")
    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<WarehouseStaff>> createStaff(@Valid @RequestBody WarehouseStaff staff) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createStaff(staff), "Staff created"));
    }

    @Operation(summary = "Update staff details")
    @PutMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<WarehouseStaff>> updateStaff(@PathVariable UUID id, @Valid @RequestBody WarehouseStaff staff) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateStaff(id, staff), "Staff updated"));
    }

    @Operation(summary = "Increment pick count for staff")
    @PutMapping("/staff/{id}/increment-picks")
    public ResponseEntity<ApiResponse<WarehouseStaff>> incrementPickCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.incrementPickCount(id), "Pick count incremented"));
    }

    @Operation(summary = "Get warehouse equipment")
    @GetMapping("/{id}/equipment")
    public ResponseEntity<ApiResponse<List<WarehouseEquipment>>> getEquipment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getEquipment(id)));
    }

    @Operation(summary = "Add equipment to warehouse")
    @PostMapping("/equipment")
    public ResponseEntity<ApiResponse<WarehouseEquipment>> createEquipment(@Valid @RequestBody WarehouseEquipment equipment) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createEquipment(equipment), "Equipment created"));
    }

    @Operation(summary = "Update equipment status")
    @PutMapping("/equipment/{id}/status")
    public ResponseEntity<ApiResponse<WarehouseEquipment>> updateEquipmentStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateEquipmentStatus(id, status), "Equipment status updated"));
    }

    @Operation(summary = "Get warehouse summary")
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
