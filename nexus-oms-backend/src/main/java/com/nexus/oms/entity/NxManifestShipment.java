package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "nx_manifest_shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxManifestShipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "manifest_id", nullable = false)
    private UUID manifestId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "tracking_number")
    private String trackingNumber;

    private String service;

    private String status;

    private BigDecimal weight;

    private BigDecimal cost;

    private String destination;
}
