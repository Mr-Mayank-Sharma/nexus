package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nxFreight_invoice_lines")
public class NxFreightInvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber;

    @Column(name = "service_level", length = 50)
    private String serviceLevel;

    @Column(name = "weight_kg", precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "dimensions_json")
    private String dimensionsJson;

    @Column(name = "origin_zip", length = 20)
    private String originZip;

    @Column(name = "destination_zip", length = 20)
    private String destinationZip;

    @Column(name = "billed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal billedAmount;

    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "variance_amount", precision = 12, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "dispute_notes")
    private String disputeNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
