package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_labor_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxLaborEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "employee_code")
    private String employeeCode;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "task_type")
    private String taskType;

    @Column(name = "status")
    private String status;

    @Column(name = "shift")
    private String shift;

    @Column(name = "clocked_in_at")
    private LocalDateTime clockedInAt;

    @Column(name = "clocked_out_at")
    private LocalDateTime clockedOutAt;

    @Column(name = "break_started_at")
    private LocalDateTime breakStartedAt;

    @Column(name = "break_ended_at")
    private LocalDateTime breakEndedAt;

    @Column(name = "total_work_minutes")
    private Integer totalWorkMinutes;

    @Column(name = "total_break_minutes")
    private Integer totalBreakMinutes;

    @Column(name = "lines_picked")
    private Integer linesPicked;

    @Column(name = "lines_packed")
    private Integer linesPacked;

    @Column(name = "units_received")
    private Integer unitsReceived;

    @Column(name = "units_shipped")
    private Integer unitsShipped;

    @Column(name = "error_count")
    private Integer errorCount;

    @Column(name = "productivity_score")
    private Double productivityScore;

    @Column(name = "efficiency_rating")
    private String efficiencyRating;

    @Column(name = "current_task")
    private String currentTask;

    @Column(name = "current_wave_id")
    private UUID currentWaveId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalWorkMinutes == null) totalWorkMinutes = 0;
        if (totalBreakMinutes == null) totalBreakMinutes = 0;
        if (linesPicked == null) linesPicked = 0;
        if (linesPacked == null) linesPacked = 0;
        if (unitsReceived == null) unitsReceived = 0;
        if (unitsShipped == null) unitsShipped = 0;
        if (errorCount == null) errorCount = 0;
        if (productivityScore == null) productivityScore = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
