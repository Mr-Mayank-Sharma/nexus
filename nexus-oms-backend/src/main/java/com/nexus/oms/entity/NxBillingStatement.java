package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_billing_statements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxBillingStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(nullable = false)
    private String currency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private String status;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount;

    @Column(name = "line_count", nullable = false)
    private Integer lineCount;

    @Column(name = "picked_lines", nullable = false)
    private Integer pickedLines;

    @Column(name = "units_handled", nullable = false)
    private Integer unitsHandled;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal total;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
        if (orderCount == null) orderCount = 0;
        if (lineCount == null) lineCount = 0;
        if (pickedLines == null) pickedLines = 0;
        if (unitsHandled == null) unitsHandled = 0;
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (total == null) total = BigDecimal.ZERO;
        if (currency == null) currency = "USD";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
