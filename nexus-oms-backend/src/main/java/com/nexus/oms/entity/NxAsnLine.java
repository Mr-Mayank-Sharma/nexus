package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_asn_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAsnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asn_id", nullable = false)
    private NxAsn asn;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "expected_qty", nullable = false)
    private Integer expectedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (receivedQty == null) receivedQty = 0;
        if (status == null) status = "PENDING";
    }
}
