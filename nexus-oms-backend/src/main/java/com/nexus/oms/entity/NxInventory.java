package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "quantity_on_hand")
    private Integer quantityOnHand;

    @Column(name = "quantity_allocated")
    private Integer quantityAllocated;

    @Column(name = "quantity_reserved")
    private Integer quantityReserved;

    @Column(name = "quantity_in_transit")
    private Integer quantityInTransit;

    @Column(name = "quantity_on_order")
    private Integer quantityOnOrder;

    @Column(name = "quantity_damaged")
    private Integer quantityDamaged;

    @Column(name = "safety_stock")
    private Integer safetyStock;

    @Column(name = "reorder_point")
    private Integer reorderPoint;

    @Column(name = "reorder_qty")
    private Integer reorderQty;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
