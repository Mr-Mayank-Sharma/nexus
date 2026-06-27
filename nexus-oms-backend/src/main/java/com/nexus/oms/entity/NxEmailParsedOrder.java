package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_email_parsed_orders")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxEmailParsedOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email_message_id")
    private String emailMessageId;

    @Column(name = "email_subject")
    private String emailSubject;

    @Column(name = "email_from")
    private String emailFrom;

    @Column(name = "email_to")
    private String emailTo;

    @Column(name = "email_received_at")
    private LocalDateTime emailReceivedAt;

    @Column(name = "attachment_filename")
    private String attachmentFilename;

    @Column(name = "attachment_type")
    private String attachmentType;

    @Column(name = "raw_body", columnDefinition = "TEXT")
    private String rawBody;

    @Column(name = "parsed_data", columnDefinition = "jsonb")
    private String parsedData;

    @Column(name = "order_id")
    private UUID orderId;

    private String status;

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "order_total")
    private BigDecimal orderTotal;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private String shippingAddress;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "matched_customer_id")
    private UUID matchedCustomerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "NEW";
        if (confidenceScore == null) confidenceScore = BigDecimal.ZERO;
        if (itemCount == null) itemCount = 0;
    }
}
