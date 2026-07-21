package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_order_rejections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxOrderRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "rejection_reason_id", nullable = false)
    private UUID rejectionReasonId;

    @Column(name = "rejection_code", nullable = false)
    private String rejectionCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "rejected_by", nullable = false)
    private String rejectedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "photo_path")
    private String photoPath;

    @Column(name = "inventory_action", nullable = false)
    private String inventoryAction; // RESTOCK, DAMAGE_WRITE_OFF, RETURN_TO_VENDOR, QUARANTINE

    @Column(name = "inventory_adjusted", nullable = false)
    private Boolean inventoryAdjusted;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, PROCESSED, CANCELLED

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (inventoryAdjusted == null) inventoryAdjusted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
