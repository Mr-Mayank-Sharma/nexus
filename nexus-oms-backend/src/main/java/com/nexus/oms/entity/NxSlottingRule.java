package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_slotting_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSlottingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @NotBlank
    @Column(nullable = false)
    private String ruleName;

    @NotBlank
    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(columnDefinition = "jsonb")
    private String criteria;

    @Column(name = "target_zone_id")
    private UUID targetZoneId;

    @Column(name = "target_bin_class")
    private String targetBinClass;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(nullable = false)
    private Double effectiveness;

    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    @Column(name = "apply_count")
    private Integer applyCount;

    private String notes;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (effectiveness == null) effectiveness = 0.0;
        if (applyCount == null) applyCount = 0;
        if (priority == null) priority = 100;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
