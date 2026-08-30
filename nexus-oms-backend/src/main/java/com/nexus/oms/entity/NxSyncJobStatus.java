package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-13: Sync job status for reconciliation reporting.
 *
 * Tracks pending / queued / completed / failed counts per sync direction so
 * operators can see the health of the bidirectional sync pipeline.
 */
@Entity
@Table(name = "nx_sync_job_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSyncJobStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "records_total")
    private Integer recordsTotal;

    @Column(name = "records_processed")
    private Integer recordsProcessed;

    @Column(name = "records_failed")
    private Integer recordsFailed;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (recordsTotal == null) recordsTotal = 0;
        if (recordsProcessed == null) recordsProcessed = 0;
        if (recordsFailed == null) recordsFailed = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
