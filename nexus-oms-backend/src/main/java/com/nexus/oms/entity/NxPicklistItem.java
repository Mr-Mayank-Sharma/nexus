package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_picklist_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxPicklistItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "picklist_id", nullable = false)
    private UUID picklistId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "picked_quantity")
    private Integer pickedQuantity;

    @Column(name = "from_bin_id")
    private UUID fromBinId;

    @Column(name = "from_location")
    private String fromLocation;

    private String status;

    @Column(name = "picked_at")
    private LocalDateTime pickedAt;

    @Column(name = "picked_by")
    private UUID pickedBy;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (pickedQuantity == null) pickedQuantity = 0;
    }
}
