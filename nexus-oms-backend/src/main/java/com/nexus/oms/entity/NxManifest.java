package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_manifests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxManifest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    private String carrier;

    @Column(name = "manifest_date")
    private String manifestDate;

    @Column(name = "bol_number")
    private String bolNumber;

    @Column(name = "total_weight")
    private BigDecimal totalWeight;

    @Column(name = "total_cost")
    private BigDecimal totalCost;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "Draft";
        if (totalWeight == null) totalWeight = BigDecimal.ZERO;
        if (totalCost == null) totalCost = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
