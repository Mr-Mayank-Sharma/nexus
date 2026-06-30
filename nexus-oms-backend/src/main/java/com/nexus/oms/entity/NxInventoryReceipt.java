package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_inventory_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxInventoryReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "node_id")
    private UUID nodeId;

    @NotBlank
    @Column(name = "receipt_type", nullable = false)
    private String receiptType;

    @Column(name = "reference_number")
    private String referenceNumber;

    @NotBlank
    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
