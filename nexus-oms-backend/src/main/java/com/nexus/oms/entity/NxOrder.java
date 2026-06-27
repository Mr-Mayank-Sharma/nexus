package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "nx_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "external_id")
    private String externalId;

    private String channel;

    @Column(name = "channel_order_id")
    private String channelOrderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private String status;

    @Column(name = "sub_status")
    private String subStatus;

    @Column(name = "fulfillment_type")
    private String fulfillmentType;

    @Column(name = "ship_from")
    private String shipFrom;

    @Column(name = "ship_to", columnDefinition = "jsonb")
    private String shipTo;

    @Column(name = "billing_address", columnDefinition = "jsonb")
    private String billingAddress;

    private String currency;

    private BigDecimal subtotal;

    @Column(name = "shipping_cost")
    private BigDecimal shippingCost;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    private BigDecimal total;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "allocated_node")
    private UUID allocatedNode;

    @Column(name = "allocation_rule")
    private String allocationRule;

    @Column(name = "allocation_confidence")
    private BigDecimal allocationConfidence;

    @Column(name = "carrier_id")
    private String carrierId;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "promised_delivery")
    private LocalDateTime promisedDelivery;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
