package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nx_productivity_log")
public class NxProductivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "labor_entry_id")
    private UUID laborEntryId;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "items_completed")
    private Integer itemsCompleted;

    @Column(name = "time_spent_minutes")
    private Integer timeSpentMinutes;

    @Column(name = "items_per_hour", precision = 8, scale = 2)
    private BigDecimal itemsPerHour;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "vs_standard_pct", precision = 8, scale = 2)
    private BigDecimal vsStandardPct;

    @CreationTimestamp
    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;
}
