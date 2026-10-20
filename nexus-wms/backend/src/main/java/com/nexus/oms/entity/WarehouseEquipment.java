package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_warehouse_equipment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @NotBlank
    @Column(nullable = false)
    private String code;

    @NotBlank
    @Column(name = "equipment_type", nullable = false)
    private String equipmentType;

    private String model;

    @NotBlank
    @Column(nullable = false)
    private String status;

    @PositiveOrZero
    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "last_maintenance_at")
    private LocalDateTime lastMaintenanceAt;

    @Column(name = "next_maintenance_at")
    private LocalDateTime nextMaintenanceAt;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "AVAILABLE";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
