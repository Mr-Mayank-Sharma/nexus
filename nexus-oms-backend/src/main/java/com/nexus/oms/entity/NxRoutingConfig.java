package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_routing_config")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxRoutingConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "default_strategy")
    private String defaultStrategy;

    @Column(name = "ai_confidence_threshold")
    private BigDecimal aiConfidenceThreshold;

    @Column(name = "enable_auto_allocation")
    private Boolean enableAutoAllocation;

    @Column(name = "enable_exception_detection")
    private Boolean enableExceptionDetection;

    @Column(name = "enable_auto_resolution")
    private Boolean enableAutoResolution;

    @Column(name = "max_splits")
    private Integer maxSplits;

    @Column(name = "cost_optimization_weight")
    private BigDecimal costOptimizationWeight;

    @Column(name = "speed_optimization_weight")
    private BigDecimal speedOptimizationWeight;

    @Column(name = "accuracy_optimization_weight")
    private BigDecimal accuracyOptimizationWeight;

    @Column(name = "preferred_carriers", columnDefinition = "jsonb")
    private String preferredCarriers;

    @Column(name = "blacklisted_nodes", columnDefinition = "jsonb")
    private String blacklistedNodes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (defaultStrategy == null) defaultStrategy = "HYBRID";
        if (aiConfidenceThreshold == null) aiConfidenceThreshold = new BigDecimal("0.7000");
        if (enableAutoAllocation == null) enableAutoAllocation = true;
        if (enableExceptionDetection == null) enableExceptionDetection = true;
        if (enableAutoResolution == null) enableAutoResolution = false;
        if (maxSplits == null) maxSplits = 3;
        if (costOptimizationWeight == null) costOptimizationWeight = new BigDecimal("0.40");
        if (speedOptimizationWeight == null) speedOptimizationWeight = new BigDecimal("0.40");
        if (accuracyOptimizationWeight == null) accuracyOptimizationWeight = new BigDecimal("0.20");
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
