package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "appointment_number", nullable = false, unique = true)
    private String appointmentNumber;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "carrier_code")
    private String carrierCode;

    @Column(name = "trailer_number")
    private String trailerNumber;

    @Column(name = "vehicle_license_plate")
    private String vehicleLicensePlate;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_phone")
    private String driverPhone;

    @Column(name = "dock_door_id")
    private UUID dockDoorId;

    @Column(name = "yard_location_id")
    private UUID yardLocationId;

    @Column(name = "estimated_arrival")
    private LocalDateTime estimatedArrival;

    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;

    @Column(name = "estimated_departure")
    private LocalDateTime estimatedDeparture;

    @Column(name = "actual_departure")
    private LocalDateTime actualDeparture;

    @Column(name = "appointment_window")
    private String appointmentWindow;

    @Column(name = "po_numbers")
    private String poNumbers;

    @Column(name = "order_ids")
    private String orderIds;

    @Column(name = "load_count")
    private Integer loadCount;

    @Column(name = "pallet_count")
    private Integer palletCount;

    @Column(name = "piece_count")
    private Integer pieceCount;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "temperature_required")
    private Boolean temperatureRequired;

    @Column(name = "temperature_min")
    private Double temperatureMin;

    @Column(name = "temperature_max")
    private Double temperatureMax;

    @Column(name = "special_instructions")
    private String specialInstructions;

    @Column(name = "checked_in_by")
    private String checkedInBy;

    @Column(name = "completed_by")
    private String completedBy;

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
        if (status == null) status = "REQUESTED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
