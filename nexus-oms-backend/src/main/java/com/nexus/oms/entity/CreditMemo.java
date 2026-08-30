package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_credit_memos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "memo_number")
    private String memoNumber;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    // T-10: Returns / exchange financial reconciliation fields.
    @Column(name = "sales_return_id")
    private UUID salesReturnId;

    @Column(name = "return_id")
    private UUID returnId;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "tax_delta", precision = 12, scale = 2)
    private BigDecimal taxDelta;

    @Column(name = "source_location_id")
    private UUID sourceLocationId;

    @Column(name = "target_location_id")
    private UUID targetLocationId;

    @Column(name = "customer_deposit_applied", precision = 12, scale = 2)
    private BigDecimal customerDepositApplied;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "memo_type")
    private String memoType;

    private String reason;

    @PositiveOrZero
    private BigDecimal amount;

    private String currency;

    private String status;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
