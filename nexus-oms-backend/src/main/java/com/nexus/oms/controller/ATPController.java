package com.nexus.oms.controller;

import com.nexus.oms.entity.NxATPRule;
import com.nexus.oms.entity.NxATPSnapshot;
import com.nexus.oms.service.ATPCalculationEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/atp")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ATPController {

    private final ATPCalculationEngine atpCalculationEngine;

    public ATPController(ATPCalculationEngine atpCalculationEngine) {
        this.atpCalculationEngine = atpCalculationEngine;
    }

    // ─── ATP Rules ─────────────────────────────────────────────────────────

    @PostMapping("/rules")
    public ResponseEntity<NxATPRule> createRule(@RequestBody NxATPRule rule) {
        return ResponseEntity.ok(atpCalculationEngine.createRule(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<NxATPRule>> getRules() {
        return ResponseEntity.ok(atpCalculationEngine.getRules());
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<NxATPRule> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(atpCalculationEngine.getRule(id));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<NxATPRule> updateRule(@PathVariable UUID id, @RequestBody NxATPRule rule) {
        return ResponseEntity.ok(atpCalculationEngine.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        atpCalculationEngine.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    // ─── ATP Calculation ───────────────────────────────────────────────────

    @GetMapping("/calculate/{nodeId}/{sku}")
    public ResponseEntity<Map<String, Object>> calculateATP(
            @PathVariable UUID nodeId,
            @PathVariable String sku) {
        return ResponseEntity.ok(atpCalculationEngine.calculateATP(nodeId, sku));
    }

    @PostMapping("/calculate/{nodeId}/bulk")
    public ResponseEntity<Map<String, Object>> calculateBulkATP(
            @PathVariable UUID nodeId,
            @RequestBody List<String> skus) {
        return ResponseEntity.ok(atpCalculationEngine.calculateBulkATP(nodeId, skus));
    }

    @GetMapping("/find-nodes")
    public ResponseEntity<List<Map<String, Object>>> findNodesWithATP(
            @RequestParam String sku,
            @RequestParam Integer requiredQuantity) {
        return ResponseEntity.ok(atpCalculationEngine.findNodesWithATP(sku, requiredQuantity));
    }

    // ─── Reservation Operations ────────────────────────────────────────────

    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Boolean>> reserveStock(
            @RequestParam UUID nodeId,
            @RequestParam String sku,
            @RequestParam int quantity) {
        boolean success = atpCalculationEngine.reserveStock(nodeId, sku, quantity);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> releaseReservation(
            @RequestParam UUID nodeId,
            @RequestParam String sku,
            @RequestParam int quantity) {
        atpCalculationEngine.releaseReservation(nodeId, sku, quantity);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/allocate")
    public ResponseEntity<Void> allocateStock(
            @RequestParam UUID nodeId,
            @RequestParam String sku,
            @RequestParam int quantity) {
        atpCalculationEngine.allocateStock(nodeId, sku, quantity);
        return ResponseEntity.ok().build();
    }

    // ─── Snapshot Operations ───────────────────────────────────────────────

    @GetMapping("/snapshots")
    public ResponseEntity<List<NxATPSnapshot>> getSnapshots(
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String sku) {
        return ResponseEntity.ok(atpCalculationEngine.getSnapshots(nodeId, sku));
    }

    @PostMapping("/snapshots")
    public ResponseEntity<NxATPSnapshot> updateSnapshot(
            @RequestParam UUID nodeId,
            @RequestParam String sku,
            @RequestParam int physicalStock) {
        return ResponseEntity.ok(atpCalculationEngine.updateSnapshot(nodeId, sku, physicalStock));
    }
}
