package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_sync_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "integration_type", nullable = false)
    private String integrationType;

    @Column(name = "sync_type", nullable = false)
    private String syncType;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "items_processed")
    private Integer itemsProcessed;

    @Column(name = "items_succeeded")
    private Integer itemsSucceeded;

    @Column(name = "items_failed")
    private Integer itemsFailed;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
        createdAt = LocalDateTime.now();
        if (itemsProcessed == null) itemsProcessed = 0;
        if (itemsSucceeded == null) itemsSucceeded = 0;
        if (itemsFailed == null) itemsFailed = 0;
    }
}
