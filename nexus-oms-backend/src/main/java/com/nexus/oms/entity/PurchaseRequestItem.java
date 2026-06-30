package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_purchase_request_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @NotBlank
    @Column(nullable = false)
    private String sku;

    @NotBlank
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Positive
    private Integer quantity;

    private String unit;

    @PositiveOrZero
    @Column(name = "estimated_unit_price")
    private BigDecimal estimatedUnitPrice;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
