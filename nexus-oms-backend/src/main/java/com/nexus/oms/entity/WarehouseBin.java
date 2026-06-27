package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_warehouse_bins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseBin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(nullable = false)
    private String code;

    @Column(name = "bin_type")
    private String binType;

    @Column(name = "bin_class")
    private String binClass;

    @Column(name = "max_weight_kg")
    private BigDecimal maxWeightKg;

    @Column(name = "max_volume_cbm")
    private BigDecimal maxVolumeCbm;

    @Column(name = "current_weight_kg")
    private BigDecimal currentWeightKg;

    @Column(name = "current_volume_cbm")
    private BigDecimal currentVolumeCbm;

    @Column(name = "is_empty")
    private Boolean isEmpty;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_reserved")
    private Boolean isReserved;

    @Column(name = "last_picked_at")
    private LocalDateTime lastPickedAt;

    @Column(name = "last_counted_at")
    private LocalDateTime lastCountedAt;

    @Column(name = "x_coord")
    private Integer xCoord;

    @Column(name = "y_coord")
    private Integer yCoord;

    @Column(name = "z_coord")
    private Integer zCoord;

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
        if (isEmpty == null) isEmpty = true;
        if (isActive == null) isActive = true;
        if (isReserved == null) isReserved = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
