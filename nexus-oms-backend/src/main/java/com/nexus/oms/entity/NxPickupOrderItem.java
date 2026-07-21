package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_pickup_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxPickupOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pickup_order_id", nullable = false)
    private UUID pickupOrderId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "picked_quantity", nullable = false)
    private Integer pickedQuantity;

    @Column(name = "location")
    private String location;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, PICKED, SUBSTITUTED, SHORT

    @Column(name = "substituted_sku")
    private String substitutedSku;

    @Column(name = "notes")
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
