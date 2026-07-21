package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nxFreight_invoices")
public class NxFreightInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "carrier_code", nullable = false, length = 30)
    private String carrierCode;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_tax", precision = 12, scale = 2)
    private BigDecimal totalTax;

    @Column(name = "total_discount", precision = 12, scale = 2)
    private BigDecimal totalDiscount;

    @Column(name = "total_net", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNet;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "matched_percentage", precision = 5, scale = 2)
    private BigDecimal matchedPercentage;

    @Column(name = "dispute_reason")
    private String disputeReason;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "file_reference", length = 200)
    private String fileReference;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
