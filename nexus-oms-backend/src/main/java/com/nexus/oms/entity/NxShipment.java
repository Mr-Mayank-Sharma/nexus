package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxShipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "carrier_id")
    private String carrierId;

    @Column(name = "service_level")
    private String serviceLevel;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "label_format")
    private String labelFormat;

    private Boolean voided;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rate;

    @Column(name = "cost_components", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String costComponents;

    @Column(name = "origin_node_id")
    private UUID originNodeId;

    @Column(name = "estimated_delivery")
    private LocalDateTime estimatedDelivery;

    @Column(name = "actual_delivery")
    private LocalDateTime actualDelivery;

    @NotBlank
    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "manifest_closed_at")
    private LocalDateTime manifestClosedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (voided == null) voided = false;
    }
}
