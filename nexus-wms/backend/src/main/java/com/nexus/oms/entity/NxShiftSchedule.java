package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "nx_shift_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxShiftSchedule {

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

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "shift_type", nullable = false)
    private String shiftType;

    @Column(name = "scheduled_start", nullable = false)
    private LocalTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalTime scheduledEnd;

    @Column(name = "actual_start")
    private LocalTime actualStart;

    @Column(name = "actual_end")
    private LocalTime actualEnd;

    @Column(name = "status")
    private String status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "SCHEDULED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
