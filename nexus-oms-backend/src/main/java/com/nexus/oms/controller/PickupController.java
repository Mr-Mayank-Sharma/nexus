package com.nexus.oms.controller;

import com.nexus.oms.entity.NxPickupOrder;
import com.nexus.oms.entity.NxPickupOrderItem;
import com.nexus.oms.entity.NxProofOfDelivery;
import com.nexus.oms.service.PickupOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/pickup")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PickupController {

    private final PickupOrderService pickupOrderService;

    public PickupController(PickupOrderService pickupOrderService) {
        this.pickupOrderService = pickupOrderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<NxPickupOrder> getPickupOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.getPickupOrder(id));
    }

    @GetMapping("/pending/{nodeId}")
    public ResponseEntity<List<NxPickupOrder>> getPendingPickups(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(pickupOrderService.getPendingPickups(nodeId));
    }

    @GetMapping("/picker/{pickerId}")
    public ResponseEntity<List<NxPickupOrder>> getPickupsByPicker(@PathVariable UUID pickerId) {
        return ResponseEntity.ok(pickupOrderService.getPickupsByPicker(pickerId));
    }

    @GetMapping("/ready")
    public ResponseEntity<List<NxPickupOrder>> getReadyForHandoff() {
        return ResponseEntity.ok(pickupOrderService.getReadyForHandoff());
    }

    @GetMapping("/code/{pickupCode}")
    public ResponseEntity<NxPickupOrder> getByPickupCode(@PathVariable String pickupCode) {
        return ResponseEntity.ok(pickupOrderService.getByPickupCode(pickupCode));
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<List<NxPickupOrderItem>> getPickupItems(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.getPickupItems(id));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<NxPickupOrder> assignPicker(
            @PathVariable UUID id,
            @RequestParam UUID pickerId,
            @RequestParam String pickerName) {
        return ResponseEntity.ok(pickupOrderService.assignPicker(id, pickerId, pickerName));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<NxPickupOrder> startPicking(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.startPicking(id));
    }

    @PostMapping("/items/{itemId}/pick")
    public ResponseEntity<NxPickupOrderItem> pickItem(
            @PathVariable UUID itemId,
            @RequestParam Integer quantity,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(pickupOrderService.pickItem(itemId, quantity, notes));
    }

    @PostMapping("/items/{itemId}/substitute")
    public ResponseEntity<NxPickupOrderItem> substituteItem(
            @PathVariable UUID itemId,
            @RequestParam String substitutedSku,
            @RequestParam Integer quantity) {
        return ResponseEntity.ok(pickupOrderService.substituteItem(itemId, substitutedSku, quantity));
    }

    @PostMapping("/{id}/complete-picking")
    public ResponseEntity<NxPickupOrder> completePicking(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.completePicking(id));
    }

    @PostMapping("/{id}/pack")
    public ResponseEntity<NxPickupOrder> packOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.packOrder(id));
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<NxPickupOrder> markReadyForHandoff(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.markReadyForHandoff(id));
    }

    @PostMapping("/{id}/handoff")
    public ResponseEntity<NxPickupOrder> handoffOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(pickupOrderService.handoffOrder(id));
    }

    @PostMapping("/{id}/collect")
    public ResponseEntity<NxProofOfDelivery> collectOrder(
            @PathVariable UUID id,
            @RequestBody NxProofOfDelivery pod) {
        return ResponseEntity.ok(pickupOrderService.collectOrder(id, pod));
    }

    @GetMapping("/{id}/pod")
    public ResponseEntity<NxProofOfDelivery> getPOD(@PathVariable UUID id) {
        NxProofOfDelivery pod = pickupOrderService.getPOD(id);
        return pod != null ? ResponseEntity.ok(pod) : ResponseEntity.notFound().build();
    }

    @GetMapping("/status/{nodeId}")
    public ResponseEntity<Map<String, Long>> getStatusCounts(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(pickupOrderService.getStatusCounts(nodeId));
    }
}
