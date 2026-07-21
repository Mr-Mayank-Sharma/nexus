package com.nexus.oms.controller;

import com.nexus.oms.entity.NxOrderRejection;
import com.nexus.oms.entity.NxRejectionReason;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.RejectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/rejections")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RejectionController {

    private final RejectionService rejectionService;

    public RejectionController(RejectionService rejectionService) {
        this.rejectionService = rejectionService;
    }

    // ─── Reasons ───────────────────────────────────────────────────────────

    @PostMapping("/reasons")
    public ResponseEntity<NxRejectionReason> createReason(@RequestBody NxRejectionReason reason) {
        return ResponseEntity.ok(rejectionService.createReason(reason));
    }

    @GetMapping("/reasons")
    public ResponseEntity<List<NxRejectionReason>> getReasons() {
        return ResponseEntity.ok(rejectionService.getReasons());
    }

    @GetMapping("/reasons/{id}")
    public ResponseEntity<NxRejectionReason> getReason(@PathVariable UUID id) {
        return ResponseEntity.ok(rejectionService.getReason(id));
    }

    @GetMapping("/reasons/category/{category}")
    public ResponseEntity<List<NxRejectionReason>> getReasonsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(rejectionService.getReasonsByCategory(category));
    }

    @PutMapping("/reasons/{id}")
    public ResponseEntity<NxRejectionReason> updateReason(
            @PathVariable UUID id,
            @RequestBody NxRejectionReason reason) {
        return ResponseEntity.ok(rejectionService.updateReason(id, reason));
    }

    @DeleteMapping("/reasons/{id}")
    public ResponseEntity<Void> deleteReason(@PathVariable UUID id) {
        rejectionService.deleteReason(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Rejections ────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<NxOrderRejection> rejectItem(@RequestBody NxOrderRejection rejection) {
        return ResponseEntity.ok(rejectionService.rejectItem(rejection));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<NxOrderRejection> processRejection(@PathVariable UUID id) {
        return ResponseEntity.ok(rejectionService.processRejection(id));
    }

    @GetMapping
    public ResponseEntity<List<NxOrderRejection>> getAllRejections() {
        return ResponseEntity.ok(rejectionService.getAllRejections());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<NxOrderRejection>> getPendingRejections() {
        return ResponseEntity.ok(rejectionService.getPendingRejections());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<NxOrderRejection>> getRejectionsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(rejectionService.getRejectionsByOrder(orderId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getRejectionStats() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(rejectionService.getRejectionStats(tenantId));
    }
}
