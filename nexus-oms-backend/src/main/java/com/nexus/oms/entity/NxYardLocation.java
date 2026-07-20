package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_yard_locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxYardLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "location_code", nullable = false)
    private String locationCode;

    @Column(name = "location_type")
    private String locationType;

    @Column(nullable = false)
    private String status;

    private Integer capacity;

    @Column(name = "current_occupancy")
    private Integer currentOccupancy;

    private String zone;

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
        if (currentOccupancy == null) currentOccupancy = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
