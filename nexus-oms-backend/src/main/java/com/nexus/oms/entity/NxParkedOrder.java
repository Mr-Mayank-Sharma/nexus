package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_parked_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxParkedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "reason", nullable = false)
    private String reason; // PREORDER, BACKORDER, FRAUD_HOLD, CREDIT_HOLD, MANUAL_HOLD

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "sku")
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "expected_date")
    private LocalDateTime expectedDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false)
    private String status; // PARKED, RELEASED, CANCELLED, CONVERTED

    @Column(name = "parked_at", nullable = false, updatable = false)
    private LocalDateTime parkedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (parkedAt == null) parkedAt = LocalDateTime.now();
        if (status == null) status = "PARKED";
        if (priority == null) priority = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
