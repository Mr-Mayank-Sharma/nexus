package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_returns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 10)
    private String grade;

    private String disposition;

    @Column(name = "carrier_id")
    private String carrierId;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "refund_reference")
    private String refundReference;

    @Column(nullable = false)
    private String status;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

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
