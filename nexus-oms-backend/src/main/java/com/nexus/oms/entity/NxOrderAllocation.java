package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_order_allocations")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxOrderAllocation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "node_type")
    private String nodeType;

    private Integer priority;

    @Column(name = "quantity_allocated")
    private Integer quantityAllocated;

    @Column(name = "quantity_requested")
    private Integer quantityRequested;

    private String status;

    @Column(name = "delivery_promise_date")
    private LocalDateTime deliveryPromiseDate;

    @Column(name = "delivery_promise_confidence")
    private BigDecimal deliveryPromiseConfidence;

    @Column(name = "allocation_strategy")
    private String allocationStrategy;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "cost_estimated")
    private BigDecimal costEstimated;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "allocated_at")
    private LocalDateTime allocatedAt;

    @Column(name = "allocated_by")
    private UUID allocatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (nodeType == null) nodeType = "WAREHOUSE";
        if (allocationStrategy == null) allocationStrategy = "RULE_BASED";
        if (priority == null) priority = 0;
        if (quantityAllocated == null) quantityAllocated = 0;
        if (quantityRequested == null) quantityRequested = 0;
        if (deliveryPromiseConfidence == null) deliveryPromiseConfidence = BigDecimal.ZERO;
        if (costEstimated == null) costEstimated = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
