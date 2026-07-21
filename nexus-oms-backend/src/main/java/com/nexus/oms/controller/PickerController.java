package com.nexus.oms.controller;

import com.nexus.oms.entity.NxPicker;
import com.nexus.oms.entity.NxPickerAssignment;
import com.nexus.oms.service.PickerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/pickers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PickerController {

    private final PickerService pickerService;

    public PickerController(PickerService pickerService) {
        this.pickerService = pickerService;
    }

    @PostMapping
    public ResponseEntity<NxPicker> createPicker(@RequestBody NxPicker picker) {
        return ResponseEntity.ok(pickerService.createPicker(picker));
    }

    @GetMapping("/{nodeId}")
    public ResponseEntity<List<NxPicker>> getPickers(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(pickerService.getPickers(nodeId));
    }

    @GetMapping("/{nodeId}/available")
    public ResponseEntity<List<NxPicker>> getAvailablePickers(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(pickerService.getAvailablePickers(nodeId));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<NxPicker> getPicker(@PathVariable UUID id) {
        return ResponseEntity.ok(pickerService.getPicker(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NxPicker> updatePicker(@PathVariable UUID id, @RequestBody NxPicker picker) {
        return ResponseEntity.ok(pickerService.updatePicker(id, picker));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<NxPicker> updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(pickerService.updateStatus(id, status));
    }

    @PostMapping("/assign/next")
    public ResponseEntity<NxPickerAssignment> assignNextAvailable(
            @RequestParam UUID nodeId,
            @RequestParam UUID pickupOrderId,
            @RequestParam String orderNumber) {
        return ResponseEntity.ok(pickerService.assignNextAvailable(nodeId, pickupOrderId, orderNumber));
    }

    @PostMapping("/assign/{pickerId}")
    public ResponseEntity<NxPickerAssignment> assignPicker(
            @PathVariable UUID pickerId,
            @RequestParam UUID pickupOrderId,
            @RequestParam String orderNumber,
            @RequestParam UUID nodeId) {
        return ResponseEntity.ok(pickerService.assignPicker(pickerId, pickupOrderId, orderNumber, nodeId));
    }

    @PostMapping("/assignments/{id}/start")
    public ResponseEntity<NxPickerAssignment> startAssignment(@PathVariable UUID id) {
        return ResponseEntity.ok(pickerService.startAssignment(id));
    }

    @PostMapping("/assignments/{id}/complete")
    public ResponseEntity<NxPickerAssignment> completeAssignment(@PathVariable UUID id) {
        return ResponseEntity.ok(pickerService.completeAssignment(id));
    }

    @GetMapping("/assignments/active/{nodeId}")
    public ResponseEntity<List<NxPickerAssignment>> getActiveAssignments(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(pickerService.getActiveAssignments(nodeId));
    }

    @GetMapping("/assignments/picker/{pickerId}")
    public ResponseEntity<List<NxPickerAssignment>> getPickerAssignments(@PathVariable UUID pickerId) {
        return ResponseEntity.ok(pickerService.getPickerAssignments(pickerId));
    }

    @GetMapping("/stats/{nodeId}")
    public ResponseEntity<Map<String, Object>> getPickerStats(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(pickerService.getPickerStats(nodeId));
    }
}
