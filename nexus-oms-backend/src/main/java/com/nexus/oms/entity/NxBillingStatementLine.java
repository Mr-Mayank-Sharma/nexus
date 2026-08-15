package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_billing_statement_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxBillingStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "rate_type", nullable = false)
    private String rateType;

    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (quantity == null) quantity = 0;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (amount == null) amount = BigDecimal.ZERO;
    }
}
