package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_slotting_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSlottingAssignment {

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
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @NotNull
    @Column(name = "bin_id", nullable = false)
    private UUID binId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "assigned_quantity")
    private Integer assignedQuantity;

    @Column(name = "velocity_class")
    private String velocityClass;

    @Column(name = "last_picked_at")
    private LocalDateTime lastPickedAt;

    @Column(name = "pick_frequency")
    private Integer pickFrequency;

    @Column(name = "last_slotting_at")
    private LocalDateTime lastSlottingAt;

    @Column(name = "slotting_score")
    private Double slottingScore;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "rule_id")
    private UUID ruleId;

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
        lastSlottingAt = LocalDateTime.now();
        if (slottingScore == null) slottingScore = 0.0;
        if (pickFrequency == null) pickFrequency = 0;
        if (assignedQuantity == null) assignedQuantity = 0;
        if (assignedBy == null) assignedBy = "SYSTEM";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
