package com.nexus.oms.controller;

import com.nexus.oms.entity.NxEndlessAisleOrder;
import com.nexus.oms.service.EndlessAisleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endless-aisle")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EndlessAisleController {

    private final EndlessAisleService endlessAisleService;

    public EndlessAisleController(EndlessAisleService endlessAisleService) {
        this.endlessAisleService = endlessAisleService;
    }

    // ─── CRUD ───────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<NxEndlessAisleOrder> createOrder(@RequestBody NxEndlessAisleOrder order) {
        return ResponseEntity.ok(endlessAisleService.createOrder(order));
    }

    @GetMapping
    public ResponseEntity<List<NxEndlessAisleOrder>> getOrders() {
        return ResponseEntity.ok(endlessAisleService.getOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NxEndlessAisleOrder> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(endlessAisleService.getOrder(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NxEndlessAisleOrder> updateOrder(@PathVariable UUID id, @RequestBody NxEndlessAisleOrder order) {
        return ResponseEntity.ok(endlessAisleService.updateOrder(id, order));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        endlessAisleService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Status Operations ──────────────────────────────────────────────

    @PutMapping("/{id}/status")
    public ResponseEntity<NxEndlessAisleOrder> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(endlessAisleService.updateStatus(id, status, notes));
    }

    // ─── Queries ────────────────────────────────────────────────────────

    @GetMapping("/status/{status}")
    public ResponseEntity<List<NxEndlessAisleOrder>> getOrdersByStatus(@PathVariable String status) {
        return ResponseEntity.ok(endlessAisleService.getOrdersByStatus(status));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<NxEndlessAisleOrder>> getOrdersByStore(@PathVariable UUID storeId) {
        return ResponseEntity.ok(endlessAisleService.getOrdersByStore(storeId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<NxEndlessAisleOrder>> getOrdersByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(endlessAisleService.getOrdersByCustomer(customerId));
    }

    // ─── Analytics ──────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(endlessAisleService.getEndlessAisleStats());
    }
}
