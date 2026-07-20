package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_dock_doors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxDockDoor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "door_number", nullable = false)
    private String doorNumber;

    @Column(name = "door_type")
    private String doorType;

    @Column(nullable = false)
    private String status;

    @Column(name = "dock_height")
    private String dockHeight;

    @Column(name = "has_leveler")
    private Boolean hasLeveler;

    @Column(name = "has_seal")
    private Boolean hasSeal;

    @Column(name = "max_width_cm")
    private Integer maxWidthCm;

    @Column(name = "max_height_cm")
    private Integer maxHeightCm;

    @Column(name = "max_weight_kg")
    private Double maxWeightKg;

    @Column(name = "current_vehicle_id")
    private UUID currentVehicleId;

    @Column(name = "current_appointment_id")
    private UUID currentAppointmentId;

    @Column(name = "zone_id")
    private UUID zoneId;

    private String notes;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "AVAILABLE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
