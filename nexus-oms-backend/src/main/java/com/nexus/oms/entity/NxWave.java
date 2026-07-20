package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_waves")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxWave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String priority;

    @Column(name = "wave_type", nullable = false)
    private String waveType;

    @Column(name = "order_count")
    private Integer orderCount;

    @Column(name = "total_line_items")
    private Integer totalLineItems;

    @Column(name = "released_line_items")
    private Integer releasedLineItems;

    @Column(name = "completed_line_items")
    private Integer completedLineItems;

    @Column(name = "zone_filter")
    private String zoneFilter;

    @Column(name = "target_completion_at")
    private LocalDateTime targetCompletionAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "released_by")
    private String releasedBy;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "optimization_score")
    private Integer optimizationScore;

    private String notes;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
        if (priority == null) priority = "NORMAL";
        if (orderCount == null) orderCount = 0;
        if (totalLineItems == null) totalLineItems = 0;
        if (releasedLineItems == null) releasedLineItems = 0;
        if (completedLineItems == null) completedLineItems = 0;
        if (optimizationScore == null) optimizationScore = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
