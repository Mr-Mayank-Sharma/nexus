package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_edi_documents")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxEdiDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    private String filename;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "parsed_status")
    private String parsedStatus;

    @Column(name = "parsed_data", columnDefinition = "jsonb")
    private String parsedData;

    @Column(name = "validation_errors", columnDefinition = "jsonb")
    private String validationErrors;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "partner_id")
    private String partnerId;

    @Column(name = "partner_name")
    private String partnerName;

    @Column(name = "control_number")
    private String controlNumber;

    @Column(name = "interchange_control_number")
    private String interchangeControlNumber;

    @Column(name = "group_control_number")
    private String groupControlNumber;

    @Column(name = "test_indicator")
    private Boolean testIndicator;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (parsedStatus == null) parsedStatus = "PENDING";
        if (testIndicator == null) testIndicator = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
