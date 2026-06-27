package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_cycle_counts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxCycleCount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "expected_qty", nullable = false)
    private Integer expectedQty;

    @Column(name = "counted_qty")
    private Integer countedQty;

    @Column(nullable = false)
    private String status;

    @Column(name = "counted_by")
    private String countedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
