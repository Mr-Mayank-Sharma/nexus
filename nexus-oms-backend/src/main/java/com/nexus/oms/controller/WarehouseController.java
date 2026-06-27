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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/warehouse")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Warehouse>>> getWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getAllWarehouses(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Warehouse>> getWarehouse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouse(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Warehouse>> createWarehouse(@RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createWarehouse(warehouse), "Warehouse created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Warehouse>> updateWarehouse(@PathVariable UUID id, @RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateWarehouse(id, warehouse), "Warehouse updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable UUID id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Warehouse deleted"));
    }

    @GetMapping("/{id}/zones")
    public ResponseEntity<ApiResponse<List<WarehouseZone>>> getZones(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getZones(id)));
    }

    @PostMapping("/zones")
    public ResponseEntity<ApiResponse<WarehouseZone>> createZone(@RequestBody WarehouseZone zone) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createZone(zone), "Zone created"));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<WarehouseZone>> updateZone(@PathVariable UUID id, @RequestBody WarehouseZone zone) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateZone(id, zone), "Zone updated"));
    }

    @GetMapping("/{id}/bins")
    public ResponseEntity<ApiResponse<List<WarehouseBin>>> getBins(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getBins(id)));
    }

    @GetMapping("/{id}/bins/empty")
    public ResponseEntity<ApiResponse<List<WarehouseBin>>> getEmptyBins(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getEmptyBins(id)));
    }

    @PostMapping("/bins")
    public ResponseEntity<ApiResponse<WarehouseBin>> createBin(@RequestBody WarehouseBin bin) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createBin(bin), "Bin created"));
    }

    @PutMapping("/bins/{id}/reserve")
    public ResponseEntity<ApiResponse<WarehouseBin>> reserveBin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.reserveBin(id), "Bin reserved"));
    }

    @PutMapping("/bins/{id}/release")
    public ResponseEntity<ApiResponse<WarehouseBin>> releaseBin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.releaseBin(id), "Bin released"));
    }

    @GetMapping("/{id}/staff")
    public ResponseEntity<ApiResponse<List<WarehouseStaff>>> getStaff(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getStaff(id)));
    }

    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<WarehouseStaff>> createStaff(@RequestBody WarehouseStaff staff) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createStaff(staff), "Staff created"));
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<WarehouseStaff>> updateStaff(@PathVariable UUID id, @RequestBody WarehouseStaff staff) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateStaff(id, staff), "Staff updated"));
    }

    @PutMapping("/staff/{id}/pick")
    public ResponseEntity<ApiResponse<WarehouseStaff>> incrementPickCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.incrementPickCount(id), "Pick count incremented"));
    }

    @GetMapping("/{id}/equipment")
    public ResponseEntity<ApiResponse<List<WarehouseEquipment>>> getEquipment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getEquipment(id)));
    }

    @PostMapping("/equipment")
    public ResponseEntity<ApiResponse<WarehouseEquipment>> createEquipment(@RequestBody WarehouseEquipment equipment) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.createEquipment(equipment), "Equipment created"));
    }

    @PutMapping("/equipment/{id}/status")
    public ResponseEntity<ApiResponse<WarehouseEquipment>> updateEquipmentStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateEquipmentStatus(id, status), "Equipment status updated"));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWarehouseSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouseSummary(id)));
    }
}
