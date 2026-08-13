package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ShipmentService;
import com.nexus.oms.service.ShipmentServiceEnhanced;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Shipments", description = "Shipment management APIs")
@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final ShipmentServiceEnhanced shippingService;

    public ShipmentController(ShipmentService shipmentService, ShipmentServiceEnhanced shippingService) {
        this.shipmentService = shipmentService;
        this.shippingService = shippingService;
    }

    @Operation(summary = "List all shipments for current tenant")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxShipment>>> getShipments() {
        return ResponseEntity.ok(ApiResponse.success(
                shipmentService.getShipmentsByTenant(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Get shipment by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxShipment>> getShipment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shipmentService.getShipment(id)));
    }

    @Operation(summary = "Get shipment by tracking number")
    @GetMapping("/tracking/{number}")
    public ResponseEntity<ApiResponse<NxShipment>> getByTracking(@PathVariable String number) {
        return ResponseEntity.ok(ApiResponse.success(shipmentService.getByTracking(number)));
    }

    @Operation(summary = "Void a shipment")
    @PostMapping("/void")
    public ResponseEntity<ApiResponse<NxShipment>> voidShipment(@RequestParam UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shipmentService.voidShipment(id), "Shipment voided"));
    }

    @Operation(summary = "Create a shipment")
    @PostMapping
    public ResponseEntity<ApiResponse<NxShipment>> createShipment(@Valid @RequestBody NxShipment shipment) {
        shipment.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(shippingService.createShipment(shipment), "Shipment created"));
    }

    @Operation(summary = "Mark a shipment as shipped")
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<NxShipment>> markShipped(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.markShipped(id), "Shipment marked shipped"));
    }

    @Operation(summary = "Mark a shipment as delivered")
    @PostMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<NxShipment>> markDelivered(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.markDelivered(id), "Shipment delivered"));
    }

    @Operation(summary = "Void a shipment by path id")
    @PostMapping("/{id}/void")
    public ResponseEntity<ApiResponse<NxShipment>> voidShipmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.voidShipment(id), "Shipment voided"));
    }
}
