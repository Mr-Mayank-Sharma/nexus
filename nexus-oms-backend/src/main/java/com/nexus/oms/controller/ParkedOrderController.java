package com.nexus.oms.controller;

import com.nexus.oms.entity.NxParkedOrder;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ParkedOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parked-orders")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ParkedOrderController {

    private final ParkedOrderService parkedOrderService;

    public ParkedOrderController(ParkedOrderService parkedOrderService) {
        this.parkedOrderService = parkedOrderService;
    }

    @PostMapping
    public ResponseEntity<NxParkedOrder> parkOrder(@RequestBody NxParkedOrder parkedOrder) {
        NxParkedOrder created = parkedOrderService.parkOrder(parkedOrder);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<NxParkedOrder>> getParkedOrders() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxParkedOrder> orders = parkedOrderService.getParkedOrders(tenantId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NxParkedOrder> getParkedOrder(@PathVariable UUID id) {
        NxParkedOrder order = parkedOrderService.getParkedOrder(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/reason/{reason}")
    public ResponseEntity<List<NxParkedOrder>> getParkedOrdersByReason(@PathVariable String reason) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxParkedOrder> orders = parkedOrderService.getParkedOrdersByReason(tenantId, reason);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<List<NxParkedOrder>> getParkedOrdersBySku(@PathVariable String sku) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxParkedOrder> orders = parkedOrderService.getParkedOrdersBySku(tenantId, sku);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<NxParkedOrder> releaseOrder(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        NxParkedOrder released = parkedOrderService.releaseOrder(id, reason);
        return ResponseEntity.ok(released);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<NxParkedOrder> cancelOrder(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        NxParkedOrder cancelled = parkedOrderService.cancelOrder(id, reason);
        return ResponseEntity.ok(cancelled);
    }

    @PutMapping("/{id}/priority")
    public ResponseEntity<NxParkedOrder> updatePriority(
            @PathVariable UUID id,
            @RequestParam Integer priority) {
        NxParkedOrder updated = parkedOrderService.updatePriority(id, priority);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/notes")
    public ResponseEntity<NxParkedOrder> updateNotes(
            @PathVariable UUID id,
            @RequestParam String notes) {
        NxParkedOrder updated = parkedOrderService.updateNotes(id, notes);
        return ResponseEntity.ok(updated);
    }
}
