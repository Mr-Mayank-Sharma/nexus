package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_pickup_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxPickupOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "picker_id")
    private UUID pickerId;

    @Column(name = "picker_name")
    private String pickerName;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, PICKING, PICKED, PACKED, READY_FOR_HANDOFF, HANDED_OFF, POD_COLLECTED, CANCELLED

    @Column(name = "pickup_type", nullable = false)
    private String pickupType; // BOPIS, BORIS

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "pickup_code")
    private String pickupCode;

    @Column(name = "estimated_ready_at")
    private LocalDateTime estimatedReadyAt;

    @Column(name = "picked_at")
    private LocalDateTime pickedAt;

    @Column(name = "packed_at")
    private LocalDateTime packedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "handed_off_at")
    private LocalDateTime handedOffAt;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

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
