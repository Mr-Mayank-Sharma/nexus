package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** One inventory publish attempt for an integration store: the header of a reconciliation report. */
@Entity
@Table(name = "nx_inventory_publish_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxInventoryPublishRun {

    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String BLOCKED = "BLOCKED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "integration_store_id", nullable = false)
    private UUID integrationStoreId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "dry_run", nullable = false)
    private Boolean dryRun;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "items_planned", nullable = false)
    @Builder.Default
    private Integer itemsPlanned = 0;

    @Column(name = "items_published", nullable = false)
    @Builder.Default
    private Integer itemsPublished = 0;

    @Column(name = "items_unchanged", nullable = false)
    @Builder.Default
    private Integer itemsUnchanged = 0;

    @Column(name = "items_failed", nullable = false)
    @Builder.Default
    private Integer itemsFailed = 0;

    @Column(name = "items_skipped", nullable = false)
    @Builder.Default
    private Integer itemsSkipped = 0;

    @Column(columnDefinition = "TEXT")
    private String message;
}
