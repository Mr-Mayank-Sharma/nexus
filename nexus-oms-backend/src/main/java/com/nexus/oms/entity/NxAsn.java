package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "nx_asns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAsn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "asn_number", nullable = false)
    private String asnNumber;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String source;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "carrier_code")
    private String carrierCode;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "purchase_order_number")
    private String purchaseOrderNumber;

    @Column(name = "ship_date")
    private LocalDateTime shipDate;

    @Column(name = "expected_arrival_date")
    private LocalDateTime expectedArrivalDate;

    @Column(name = "edi_document_id")
    private UUID ediDocumentId;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "asn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NxAsnLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
        if (source == null) source = "MANUAL";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
