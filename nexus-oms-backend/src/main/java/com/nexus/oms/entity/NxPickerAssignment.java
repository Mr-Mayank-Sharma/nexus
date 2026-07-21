package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_picker_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxPickerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "picker_id", nullable = false)
    private UUID pickerId;

    @Column(name = "pickup_order_id", nullable = false)
    private UUID pickupOrderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "status", nullable = false)
    private String status; // ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (assignedAt == null) assignedAt = LocalDateTime.now();
        if (status == null) status = "ASSIGNED";
        if (priority == null) priority = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
