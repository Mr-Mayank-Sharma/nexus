package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_brokering_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxBrokeringRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_type", nullable = false)
    private String runType; // SCHEDULED, MANUAL, PRIORITY

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "orders_processed")
    private Integer ordersProcessed;

    @Column(name = "orders_allocated")
    private Integer ordersAllocated;

    @Column(name = "orders_failed")
    private Integer ordersFailed;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(nullable = false)
    private String status; // RUNNING, COMPLETED, FAILED

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "RUNNING";
        if (ordersProcessed == null) ordersProcessed = 0;
        if (ordersAllocated == null) ordersAllocated = 0;
        if (ordersFailed == null) ordersFailed = 0;
    }
}
