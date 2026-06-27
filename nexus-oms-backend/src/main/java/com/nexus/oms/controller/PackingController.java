package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.service.PackingService;
import com.nexus.oms.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/packing")
public class PackingController {

    private final PackingService packingService;

    public PackingController(PackingService packingService) {
        this.packingService = packingService;
    }

    @GetMapping("/packages")
    public ResponseEntity<ApiResponse<List<NxPackage>>> getPackages(@RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxPackage> result = status != null
                ? packingService.getPackagesByStatus(tenantId, status)
                : packingService.getPackages(tenantId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/packages/{id}")
    public ResponseEntity<ApiResponse<NxPackage>> getPackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(packingService.getPackage(id)));
    }

    @PostMapping("/packages")
    public ResponseEntity<ApiResponse<NxPackage>> createPackage(@RequestBody NxPackage pkg) {
        pkg.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(packingService.createPackage(pkg), "Package created"));
    }

    @PostMapping("/packages/{id}/start")
    public ResponseEntity<ApiResponse<NxPackage>> startPacking(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(packingService.startPacking(id), "Packing started"));
    }

    @PostMapping("/packages/{id}/items")
    public ResponseEntity<ApiResponse<NxPackage>> addItem(@PathVariable UUID id, @RequestBody String itemJson) {
        return ResponseEntity.ok(ApiResponse.success(packingService.addItem(id, itemJson), "Item added"));
    }

    @PostMapping("/packages/{id}/complete")
    public ResponseEntity<ApiResponse<NxPackage>> completePacking(@PathVariable UUID id, @RequestParam String packedBy) {
        return ResponseEntity.ok(ApiResponse.success(packingService.completePacking(id, packedBy), "Packing completed"));
    }

    @PostMapping("/packages/{id}/label")
    public ResponseEntity<ApiResponse<NxPackage>> generateLabel(@PathVariable UUID id,
                                                                  @RequestParam String carrierId,
                                                                  @RequestParam String carrierName,
                                                                  @RequestParam String serviceLevel,
                                                                  @RequestParam String trackingNumber,
                                                                  @RequestParam(required = false) String labelUrl) {
        return ResponseEntity.ok(ApiResponse.success(
                packingService.generateLabel(id, carrierId, carrierName, serviceLevel, trackingNumber, labelUrl),
                "Label generated"));
    }

    @PostMapping("/packages/{id}/ship")
    public ResponseEntity<ApiResponse<NxPackage>> shipPackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(packingService.shipPackage(id), "Package shipped"));
    }

    @PostMapping("/packages/{id}/void")
    public ResponseEntity<ApiResponse<NxPackage>> voidPackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(packingService.voidPackage(id), "Package voided"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(packingService.getDashboardKPIs(TenantContext.getCurrentTenantId())));
    }
}
