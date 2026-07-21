package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_atp_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxATPSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "physical_stock", nullable = false)
    private Integer physicalStock;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock;

    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    @Column(name = "allocated_stock", nullable = false)
    private Integer allocatedStock;

    @Column(name = "atp_quantity", nullable = false)
    private Integer atpQuantity;

    @Column(name = "total_demand", nullable = false)
    private Integer totalDemand;

    @Column(name = "net_atp", nullable = false)
    private Integer netATP;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDateTime snapshotDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (snapshotDate == null) snapshotDate = LocalDateTime.now();
    }
}
