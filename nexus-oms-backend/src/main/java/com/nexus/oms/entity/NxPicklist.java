package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_picklists")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxPicklist {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(name = "wave_type")
    private String waveType;

    private String priority;

    private String status;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "total_items")
    private Integer totalItems;

    @Column(name = "picked_items")
    private Integer pickedItems;

    @Column(name = "order_ids", columnDefinition = "UUID[]")
    private String orderIds;

    private String notes;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
        if (priority == null) priority = "NORMAL";
        if (waveType == null) waveType = "SINGLE_ORDER";
        if (totalItems == null) totalItems = 0;
        if (pickedItems == null) pickedItems = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
