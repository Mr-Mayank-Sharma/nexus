package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_fulfillment_capacity_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxFulfillmentCapacityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(nullable = false)
    private String action; // ORDER_ASSIGNED, ORDER_REMOVED, LIMIT_REACHED, LIMIT_RESET

    @Column(name = "orders_before")
    private Integer ordersBefore;

    @Column(name = "orders_after")
    private Integer ordersAfter;

    @Column(name = "capacity_percentage")
    private BigDecimal capacityPercentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
