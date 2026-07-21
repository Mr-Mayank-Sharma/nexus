package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_fulfillment_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxFulfillmentLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "max_orders_per_day")
    private Integer maxOrdersPerDay;

    @Column(name = "max_orders_per_week")
    private Integer maxOrdersPerWeek;

    @Column(name = "max_items_per_day")
    private Integer maxItemsPerDay;

    @Column(name = "current_orders_today")
    private Integer currentOrdersToday;

    @Column(name = "current_orders_this_week")
    private Integer currentOrdersThisWeek;

    @Column(name = "current_items_today")
    private Integer currentItemsToday;

    @Column(name = "fulfillment_enabled", nullable = false)
    private Boolean fulfillmentEnabled;

    @Column(name = "alert_threshold")
    private BigDecimal alertThreshold;

    @Column(name = "last_reset_at")
    private LocalDateTime lastResetAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (fulfillmentEnabled == null) fulfillmentEnabled = true;
        if (currentOrdersToday == null) currentOrdersToday = 0;
        if (currentOrdersThisWeek == null) currentOrdersThisWeek = 0;
        if (currentItemsToday == null) currentItemsToday = 0;
        if (alertThreshold == null) alertThreshold = new BigDecimal("0.80");
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
