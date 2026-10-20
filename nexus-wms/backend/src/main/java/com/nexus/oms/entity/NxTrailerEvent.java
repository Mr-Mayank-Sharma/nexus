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
@Table(name = "nx_trailer_events")
public class NxTrailerEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "trailer_number", nullable = false, length = 50)
    private String trailerNumber;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "dock_door_id")
    private UUID dockDoorId;

    @Column(name = "yard_location_id")
    private UUID yardLocationId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "carrier_code", length = 30)
    private String carrierCode;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "seal_number", length = 50)
    private String sealNumber;

    @Column(name = "license_plate", length = 30)
    private String licensePlate;

    @Column(name = "loaded")
    private Boolean loaded;

    @Column(name = "pallet_count")
    private Integer palletCount;

    @Column(name = "weight_kg", precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "condition_notes")
    private String conditionNotes;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
