package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-10: A credit memo's application to a single invoice.
 *
 * A return claim can generate MULTIPLE credit memos, and each credit memo can
 * apply to MULTIPLE invoices. This replaces the single-invoice assumption on
 * the legacy CreditMemo entity, which broke accounting for multi-transaction
 * returns.
 */
@Entity
@Table(name = "nx_credit_memo_invoice_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxCreditMemoInvoiceApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "credit_memo_id", nullable = false)
    private UUID creditMemoId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (taxAmount == null) taxAmount = BigDecimal.ZERO;
    }
}
