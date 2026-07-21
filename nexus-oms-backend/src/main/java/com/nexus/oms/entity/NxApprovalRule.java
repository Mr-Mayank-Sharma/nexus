package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_approval_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    private String name;

    @Column(name = "rule_type", nullable = false)
    private String ruleType; // AMOUNT_THRESHOLD, VELOCITY, GEOLOCATION, FRAUD_SCORE, BLACKLIST, REPEAT_CUSTOMER

    @Column(name = "action", nullable = false)
    private String action; // AUTO_APPROVE, HOLD_FOR_REVIEW, REJECT

    @Column(name = "threshold_value", precision = 12, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_string")
    private String thresholdString;

    @Column(name = "priority", nullable = false)
    private Integer priority;

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
