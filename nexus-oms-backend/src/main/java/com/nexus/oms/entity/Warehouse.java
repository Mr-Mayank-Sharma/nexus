package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_warehouses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotBlank
    @Column(nullable = false)
    private String code;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String type;

    private String status;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;

    private String state;

    @Column(name = "zip_code")
    private String zipCode;

    private String country;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "total_capacity_sqm")
    private BigDecimal totalCapacitySqm;

    @Column(name = "used_capacity_sqm")
    private BigDecimal usedCapacitySqm;

    @Column(name = "total_capacity_cbm")
    private BigDecimal totalCapacityCbm;

    @Column(name = "used_capacity_cbm")
    private BigDecimal usedCapacityCbm;

    @Column(name = "dock_count")
    private Integer dockCount;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "operating_hours", columnDefinition = "jsonb")
    private String operatingHours;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Email
    @Column(name = "contact_email")
    private String contactEmail;

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
        if (status == null) status = "ACTIVE";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
