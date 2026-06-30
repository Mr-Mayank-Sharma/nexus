package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_purchase_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @NotBlank
    @Column(nullable = false)
    private String sku;

    @NotBlank
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Positive
    @Column(name = "quantity_ordered")
    private Integer quantityOrdered;

    @PositiveOrZero
    @Column(name = "quantity_received")
    private Integer quantityReceived;

    @PositiveOrZero
    @Column(name = "quantity_cancelled")
    private Integer quantityCancelled;

    @PositiveOrZero
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @PositiveOrZero
    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @PositiveOrZero
    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @PositiveOrZero
    @Column(name = "discount_percent")
    private BigDecimal discountPercent;

    private String unit;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (quantityReceived == null) quantityReceived = 0;
        if (quantityCancelled == null) quantityCancelled = 0;
    }
}
