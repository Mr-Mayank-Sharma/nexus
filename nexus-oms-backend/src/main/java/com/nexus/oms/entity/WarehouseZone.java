package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_warehouse_zones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "zone_type")
    private String zoneType;

    @Column(name = "zone_category")
    private String zoneCategory;

    @Column(name = "temperature_min")
    private BigDecimal temperatureMin;

    @Column(name = "temperature_max")
    private BigDecimal temperatureMax;

    @Column(name = "humidity_min")
    private BigDecimal humidityMin;

    @Column(name = "humidity_max")
    private BigDecimal humidityMax;

    @Column(name = "capacity_sqm")
    private BigDecimal capacitySqm;

    @Column(name = "used_sqm")
    private BigDecimal usedSqm;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_hazardous")
    private Boolean isHazardous;

    @Column(name = "security_level")
    private String securityLevel;

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
        if (isActive == null) isActive = true;
        if (isHazardous == null) isHazardous = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
