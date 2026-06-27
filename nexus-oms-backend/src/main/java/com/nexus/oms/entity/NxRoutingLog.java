package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_routing_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxRoutingLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String strategy;

    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    private String inputSnapshot;

    @Column(name = "rules_evaluated", columnDefinition = "jsonb")
    private String rulesEvaluated;

    @Column(columnDefinition = "jsonb")
    private String candidates;

    @Column(name = "selected_node_id")
    private UUID selectedNodeId;

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    @Column(name = "delivery_promise_estimate")
    private LocalDateTime deliveryPromiseEstimate;

    @Column(name = "cost_estimate")
    private BigDecimal costEstimate;

    @Column(name = "exceptions_detected", columnDefinition = "jsonb")
    private String exceptionsDetected;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "SUCCESS";
    }
}
