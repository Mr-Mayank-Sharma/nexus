package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_atp_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxATPRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    private String name;

    @Column(name = "rule_type", nullable = false)
    private String ruleType; // HARD_RESERVE, SOFT_RESERVE, SAFETY_STOCK, ALLOCATION_WINDOW

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "safety_stock_pct", precision = 5, scale = 2)
    private BigDecimal safetyStockPercentage;

    @Column(name = "reserve_window_hours")
    private Integer reserveWindowHours;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
