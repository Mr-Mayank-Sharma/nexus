package com.nexus.oms.controller;

import com.nexus.oms.entity.NxFulfillmentCapacityLog;
import com.nexus.oms.entity.NxFulfillmentLimit;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.FulfillmentLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillment-limits")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FulfillmentLimitController {

    private final FulfillmentLimitService fulfillmentLimitService;

    public FulfillmentLimitController(FulfillmentLimitService fulfillmentLimitService) {
        this.fulfillmentLimitService = fulfillmentLimitService;
    }

    @PostMapping
    public ResponseEntity<NxFulfillmentLimit> createLimit(@RequestBody NxFulfillmentLimit limit) {
        NxFulfillmentLimit created = fulfillmentLimitService.createLimit(limit);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<NxFulfillmentLimit>> getLimits() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxFulfillmentLimit> limits = fulfillmentLimitService.getLimits(tenantId);
        return ResponseEntity.ok(limits);
    }

    @GetMapping("/{nodeId}")
    public ResponseEntity<NxFulfillmentLimit> getLimit(@PathVariable UUID nodeId) {
        NxFulfillmentLimit limit = fulfillmentLimitService.getLimit(nodeId);
        return ResponseEntity.ok(limit);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NxFulfillmentLimit> updateLimit(
            @PathVariable UUID id,
            @RequestBody NxFulfillmentLimit updates) {
        NxFulfillmentLimit limit = fulfillmentLimitService.updateLimit(id, updates);
        return ResponseEntity.ok(limit);
    }

    @GetMapping("/{nodeId}/capacity")
    public ResponseEntity<Map<String, Object>> checkCapacity(@PathVariable UUID nodeId) {
        Map<String, Object> capacity = fulfillmentLimitService.checkCapacity(nodeId);
        return ResponseEntity.ok(capacity);
    }

    @PutMapping("/{nodeId}/toggle")
    public ResponseEntity<NxFulfillmentLimit> toggleFulfillment(
            @PathVariable UUID nodeId,
            @RequestParam boolean enabled) {
        NxFulfillmentLimit limit = fulfillmentLimitService.toggleFulfillment(nodeId, enabled);
        return ResponseEntity.ok(limit);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<NxFulfillmentCapacityLog>> getCapacityAlerts() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxFulfillmentCapacityLog> alerts = fulfillmentLimitService.getCapacityAlerts(tenantId);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/history/{nodeId}")
    public ResponseEntity<List<NxFulfillmentCapacityLog>> getCapacityHistory(
            @PathVariable UUID nodeId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDateTime startTime = start != null ? LocalDateTime.parse(start) : LocalDateTime.now().minusDays(7);
        LocalDateTime endTime = end != null ? LocalDateTime.parse(end) : LocalDateTime.now();
        List<NxFulfillmentCapacityLog> history = fulfillmentLimitService.getCapacityHistory(nodeId, startTime, endTime);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetDailyCounts() {
        fulfillmentLimitService.resetDailyCounts();
        return ResponseEntity.ok().build();
    }
}
