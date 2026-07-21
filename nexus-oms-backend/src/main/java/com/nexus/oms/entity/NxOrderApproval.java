package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_order_approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxOrderApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "order_total", precision = 12, scale = 2)
    private BigDecimal orderTotal;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, APPROVED, REJECTED, MANUAL_REVIEW

    @Column(name = "matched_rules", columnDefinition = "TEXT")
    private String matchedRules;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
