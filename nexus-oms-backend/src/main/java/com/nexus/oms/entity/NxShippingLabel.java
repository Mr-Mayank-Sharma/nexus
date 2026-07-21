package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_shipping_labels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxShippingLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "pickup_order_id")
    private UUID pickupOrderId;

    @Column(name = "carrier", nullable = false)
    private String carrier; // FEDEX, UPS, USPS, DHL, LOCAL

    @Column(name = "service_type", nullable = false)
    private String serviceType; // STANDARD, EXPRESS, OVERNIGHT, SAME_DAY

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "label_base64", columnDefinition = "TEXT")
    private String labelBase64;

    @Column(name = "status", nullable = false)
    private String status; // GENERATED, PRINTED, ATTACHED, CANCELLED

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    @Column(name = "attached_at")
    private LocalDateTime attachedAt;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "to_name")
    private String toName;

    @Column(name = "to_address")
    private String toAddress;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "dimensions")
    private String dimensions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (generatedAt == null) generatedAt = LocalDateTime.now();
        if (status == null) status = "GENERATED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
