package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_pickers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxPicker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "status", nullable = false)
    private String status; // AVAILABLE, PICKING, ON_BREAK, OFFLINE

    @Column(name = "current_order_id")
    private UUID currentOrderId;

    @Column(name = "max_concurrent_orders", nullable = false)
    private Integer maxConcurrentOrders;

    @Column(name = "orders_completed_today", nullable = false)
    private Integer ordersCompletedToday;

    @Column(name = "items_picked_today", nullable = false)
    private Integer itemsPickedToday;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "shift_start")
    private LocalDateTime shiftStart;

    @Column(name = "shift_end")
    private LocalDateTime shiftEnd;

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
        if (status == null) status = "AVAILABLE";
        if (active == null) active = true;
        if (maxConcurrentOrders == null) maxConcurrentOrders = 3;
        if (ordersCompletedToday == null) ordersCompletedToday = 0;
        if (itemsPickedToday == null) itemsPickedToday = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
