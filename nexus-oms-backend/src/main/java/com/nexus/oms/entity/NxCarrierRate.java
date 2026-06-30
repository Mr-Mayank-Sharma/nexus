package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_carrier_rates")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxCarrierRate {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(name = "carrier_code", nullable = false)
    private String carrierCode;

    @NotBlank
    @Column(name = "carrier_name", nullable = false)
    private String carrierName;

    @NotBlank
    @Column(name = "service_level", nullable = false)
    private String serviceLevel;

    @NotBlank
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    private String zone;

    @Column(name = "weight_min_kg")
    private BigDecimal weightMinKg;

    @Column(name = "weight_max_kg")
    private BigDecimal weightMaxKg;

    @NotNull
    @Positive
    @Column(name = "base_rate", nullable = false)
    private BigDecimal baseRate;

    @Column(name = "per_kg_rate")
    private BigDecimal perKgRate;

    @Column(name = "fuel_surcharge_pct")
    private BigDecimal fuelSurchargePct;

    @Column(name = "residential_surcharge")
    private BigDecimal residentialSurcharge;

    @Column(name = "transit_days_min")
    private Integer transitDaysMin;

    @Column(name = "transit_days_max")
    private Integer transitDaysMax;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

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
        if (weightMinKg == null) weightMinKg = BigDecimal.ZERO;
        if (weightMaxKg == null) weightMaxKg = new BigDecimal("99999");
        if (perKgRate == null) perKgRate = BigDecimal.ZERO;
        if (fuelSurchargePct == null) fuelSurchargePct = BigDecimal.ZERO;
        if (residentialSurcharge == null) residentialSurcharge = BigDecimal.ZERO;
        if (transitDaysMin == null) transitDaysMin = 1;
        if (transitDaysMax == null) transitDaysMax = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
