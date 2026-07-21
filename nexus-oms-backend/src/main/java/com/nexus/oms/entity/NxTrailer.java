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
@Table(name = "nx_trailers")
public class NxTrailer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "trailer_number", nullable = false, length = 50)
    private String trailerNumber;

    @Column(name = "carrier_code", length = 30)
    private String carrierCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "current_dock_door_id")
    private UUID currentDockDoorId;

    @Column(name = "current_yard_location_id")
    private UUID currentYardLocationId;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @Column(name = "docked_at")
    private LocalDateTime dockedAt;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    @Column(name = "loaded")
    private Boolean loaded;

    @Column(name = "pallet_count")
    private Integer palletCount;

    @Column(name = "seal_number", length = 50)
    private String sealNumber;

    @Column(name = "license_plate", length = 30)
    private String licensePlate;

    @Column(name = "dwelled_minutes")
    private Integer dwelledMinutes;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
