package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Shipments", description = "Shipment management APIs")
@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
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
}
