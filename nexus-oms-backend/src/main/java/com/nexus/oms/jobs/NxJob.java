package com.nexus.oms.jobs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_jobs")
@Getter
@Setter
@NoArgsConstructor
public class NxJob {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "job_type", nullable = false, length = 60)
    private String jobType;
    /** JSON object of the job's parameters. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String params = "{}";
    /** Six-field cron (second minute hour day month weekday); null means only on demand. */
    @Column(length = 80)
    private String cron;
    @Column(nullable = false, length = 60)
    private String timezone = "UTC";
    @Column(nullable = false)
    private Boolean enabled = false;
    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;
    @Column(name = "last_status", length = 20)
    private String lastStatus;
    @Column(name = "created_by", length = 120)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
