package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxShipment>> getShipment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shipmentService.getShipment(id)));
    }

    @GetMapping("/tracking/{number}")
    public ResponseEntity<ApiResponse<NxShipment>> getByTracking(@PathVariable String number) {
        return ResponseEntity.ok(ApiResponse.success(shipmentService.getByTracking(number)));
    }

    @PostMapping("/void")
    public ResponseEntity<ApiResponse<NxShipment>> voidShipment(@RequestParam UUID id) {
        return ResponseEntity.ok(ApiResponse.success(shipmentService.voidShipment(id), "Shipment voided"));
    }
}
