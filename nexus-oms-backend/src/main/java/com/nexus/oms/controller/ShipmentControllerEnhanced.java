package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.service.ShipmentServiceEnhanced;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/shipping")
public class ShipmentControllerEnhanced {

    private final ShipmentServiceEnhanced shippingService;

    public ShipmentControllerEnhanced(ShipmentServiceEnhanced shippingService) {
        this.shippingService = shippingService;
    }

    @GetMapping("/shipments")
    public ResponseEntity<ApiResponse<List<NxShipment>>> getShipments() {
        return ResponseEntity.ok(ApiResponse.success(
                shippingService.getShipments(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/shipments/{id}")
    public ResponseEntity<ApiResponse<NxShipment>> getShipment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.getShipment(id)));
    }

    @GetMapping("/orders/{orderId}/shipments")
    public ResponseEntity<ApiResponse<List<NxShipment>>> getShipmentsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.getShipmentsByOrder(orderId)));
    }

    @PostMapping("/shipments")
    public ResponseEntity<ApiResponse<NxShipment>> createShipment(@Valid @RequestBody NxShipment shipment) {
        shipment.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(shippingService.createShipment(shipment), "Shipment created"));
    }

    @PostMapping("/shipments/{id}/tracking")
    public ResponseEntity<ApiResponse<NxShipment>> updateTracking(@PathVariable UUID id,
                                                                    @RequestParam String trackingNumber,
                                                                    @RequestParam String carrierId,
                                                                    @RequestParam(required = false) String serviceLevel) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingService.updateTracking(id, trackingNumber, carrierId, serviceLevel), "Tracking updated"));
    }

    @PostMapping("/shipments/{id}/ship")
    public ResponseEntity<ApiResponse<NxShipment>> markShipped(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.markShipped(id), "Shipment marked shipped"));
    }

    @PostMapping("/shipments/{id}/deliver")
    public ResponseEntity<ApiResponse<NxShipment>> markDelivered(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.markDelivered(id), "Shipment delivered"));
    }

    @PostMapping("/shipments/{id}/void")
    public ResponseEntity<ApiResponse<NxShipment>> voidShipment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.voidShipment(id), "Shipment voided"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(shippingService.getDashboardKPIs(TenantContext.getCurrentTenantId())));
    }
}
