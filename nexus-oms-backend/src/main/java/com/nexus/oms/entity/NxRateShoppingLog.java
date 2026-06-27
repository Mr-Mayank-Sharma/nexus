package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_rate_shopping_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxRateShoppingLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "from_zip")
    private String fromZip;

    @Column(name = "to_zip")
    private String toZip;

    @Column(name = "to_country")
    private String toCountry;

    @Column(name = "total_weight_kg")
    private BigDecimal totalWeightKg;

    @Column(name = "declared_value")
    private BigDecimal declaredValue;

    @Column(name = "num_packages")
    private Integer numPackages;

    @Column(columnDefinition = "jsonb")
    private String results;

    @Column(name = "selected_carrier_code")
    private String selectedCarrierCode;

    @Column(name = "selected_service")
    private String selectedService;

    @Column(name = "total_cost")
    private BigDecimal totalCost;

    @Column(name = "estimated_delivery_days")
    private Integer estimatedDeliveryDays;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (numPackages == null) numPackages = 1;
    }
}
