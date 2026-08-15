package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_rate_cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxRateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "client_name")
    private String clientName;

    @Column(nullable = false)
    private String currency;

    @Column(name = "per_order_fee", nullable = false)
    private BigDecimal perOrderFee;

    @Column(name = "per_line_fee", nullable = false)
    private BigDecimal perLineFee;

    @Column(name = "picking_fee_per_line", nullable = false)
    private BigDecimal pickingFeePerLine;

    @Column(name = "storage_fee_per_unit", nullable = false)
    private BigDecimal storageFeePerUnit;

    private String description;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (currency == null) currency = "USD";
        if (perOrderFee == null) perOrderFee = BigDecimal.ZERO;
        if (perLineFee == null) perLineFee = BigDecimal.ZERO;
        if (pickingFeePerLine == null) pickingFeePerLine = BigDecimal.ZERO;
        if (storageFeePerUnit == null) storageFeePerUnit = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
