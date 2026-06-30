package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_carriers")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxCarrier {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", columnDefinition = "TEXT")
    private String apiSecretEncrypted;

    @Column(name = "otd_rate")
    private BigDecimal otdRate;

    @Column(name = "avg_cost")
    private BigDecimal avgCost;

    @Column(name = "total_shipments")
    private Long totalShipments;

    @Column(name = "damage_rate")
    private BigDecimal damageRate;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "is_active")
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
        if (status == null) status = "ACTIVE";
        if (otdRate == null) otdRate = BigDecimal.ZERO;
        if (avgCost == null) avgCost = BigDecimal.ZERO;
        if (totalShipments == null) totalShipments = 0L;
        if (damageRate == null) damageRate = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
