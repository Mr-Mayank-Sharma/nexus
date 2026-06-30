package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxCarrier;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.CarrierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/carriers")
public class CarrierController {

    private final CarrierService carrierService;

    public CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NxCarrier>>> getCarriers() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxCarrier> carriers = carrierService.getCarriers(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        return ResponseEntity.ok(ApiResponse.success(carriers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCarrier>> getCarrier(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(carrierService.getCarrier(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxCarrier>> createCarrier(@Valid @RequestBody NxCarrier carrier) {
        return ResponseEntity.ok(ApiResponse.success(carrierService.createCarrier(carrier)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCarrier>> updateCarrier(
            @PathVariable UUID id, @RequestBody NxCarrier updates) {
        return ResponseEntity.ok(ApiResponse.success(carrierService.updateCarrier(id, updates)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCarrier(@PathVariable UUID id) {
        carrierService.deleteCarrier(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Carrier deleted"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(carrierService.getCarrierKPIs(tenantId)));
    }
}
