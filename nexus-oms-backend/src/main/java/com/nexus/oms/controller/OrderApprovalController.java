package com.nexus.oms.controller;

import com.nexus.oms.entity.NxApprovalRule;
import com.nexus.oms.entity.NxOrderApproval;
import com.nexus.oms.service.OrderApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/approvals")
@CrossOrigin(origins = "*", maxAge = 3600)
public class OrderApprovalController {

    private final OrderApprovalService orderApprovalService;

    public OrderApprovalController(OrderApprovalService orderApprovalService) {
        this.orderApprovalService = orderApprovalService;
    }

    @PostMapping("/rules")
    public ResponseEntity<NxApprovalRule> createRule(@RequestBody NxApprovalRule rule) {
        return ResponseEntity.ok(orderApprovalService.createRule(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<NxApprovalRule>> getRules() {
        return ResponseEntity.ok(orderApprovalService.getRules());
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<NxApprovalRule> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(orderApprovalService.getRule(id));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<NxApprovalRule> updateRule(@PathVariable UUID id, @RequestBody NxApprovalRule rule) {
        return ResponseEntity.ok(orderApprovalService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        orderApprovalService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    public ResponseEntity<NxOrderApproval> evaluateOrder(
            @RequestParam UUID orderId,
            @RequestParam String orderNumber,
            @RequestParam BigDecimal orderTotal,
            @RequestParam(required = false) UUID customerId) {
        return ResponseEntity.ok(orderApprovalService.evaluateOrder(orderId, orderNumber, orderTotal, customerId));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<NxOrderApproval> manualReview(
            @PathVariable UUID id,
            @RequestParam String decision,
            @RequestParam String reviewer,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(orderApprovalService.manualReview(id, decision, reviewer, notes));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<NxOrderApproval>> getPendingReviews() {
        return ResponseEntity.ok(orderApprovalService.getPendingReviews());
    }

    @GetMapping
    public ResponseEntity<List<NxOrderApproval>> getAllApprovals() {
        return ResponseEntity.ok(orderApprovalService.getAllApprovals());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<NxOrderApproval> getApproval(@PathVariable UUID orderId) {
        NxOrderApproval approval = orderApprovalService.getApproval(orderId);
        return approval != null ? ResponseEntity.ok(approval) : ResponseEntity.notFound().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getApprovalStats() {
        return ResponseEntity.ok(orderApprovalService.getApprovalStats());
    }
}
