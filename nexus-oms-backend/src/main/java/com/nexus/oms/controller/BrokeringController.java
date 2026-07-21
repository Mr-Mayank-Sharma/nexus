package com.nexus.oms.controller;

import com.nexus.oms.entity.NxBrokeringQueue;
import com.nexus.oms.entity.NxBrokeringRun;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.BrokeringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brokering")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BrokeringController {

    private final BrokeringService brokeringService;

    public BrokeringController(BrokeringService brokeringService) {
        this.brokeringService = brokeringService;
    }

    @PostMapping("/enqueue")
    public ResponseEntity<NxBrokeringQueue> enqueueOrder(
            @RequestParam UUID orderId,
            @RequestParam(defaultValue = "NORMAL") String priority) {
        NxBrokeringQueue queue = brokeringService.enqueueOrder(orderId, priority);
        return ResponseEntity.ok(queue);
    }

    @PostMapping("/process")
    public ResponseEntity<NxBrokeringRun> processBrokeringQueue() {
        NxBrokeringRun run = brokeringService.processBrokeringQueue();
        return ResponseEntity.ok(run);
    }

    @PostMapping("/process/priority")
    public ResponseEntity<NxBrokeringRun> processPriorityQueue() {
        NxBrokeringRun run = brokeringService.processPriorityQueue();
        return ResponseEntity.ok(run);
    }

    @PostMapping("/process/manual")
    public ResponseEntity<NxBrokeringRun> manualBrokeringRun(@RequestBody List<UUID> orderIds) {
        NxBrokeringRun run = brokeringService.manualBrokeringRun(orderIds);
        return ResponseEntity.ok(run);
    }

    @GetMapping("/queue")
    public ResponseEntity<List<NxBrokeringQueue>> getQueue(
            @RequestParam(required = false) String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxBrokeringQueue> queue = brokeringService.getQueue(tenantId, status);
        return ResponseEntity.ok(queue);
    }

    @GetMapping("/queue/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> stats = brokeringService.getQueueStats(tenantId);
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/queue/{id}")
    public ResponseEntity<Void> removeFromQueue(@PathVariable UUID id) {
        brokeringService.removeFromQueue(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/runs")
    public ResponseEntity<List<NxBrokeringRun>> getRunHistory() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxBrokeringRun> runs = brokeringService.getRunHistory(tenantId);
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<NxBrokeringRun> getRun(@PathVariable UUID id) {
        NxBrokeringRun run = brokeringService.getRun(id);
        return ResponseEntity.ok(run);
    }

    @PostMapping("/expire-stale")
    public ResponseEntity<List<NxBrokeringQueue>> expireStaleOrders() {
        List<NxBrokeringQueue> expired = brokeringService.expireStaleOrders();
        return ResponseEntity.ok(expired);
    }
}
