package com.nexus.oms.pos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** The latest quantity a POS reported for one SKU. History is not kept: applied corrections are in the inventory ledger. */
@Entity
@Table(name = "nx_pos_inventory_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class NxPosInventorySnapshot {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(nullable = false, length = 128)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();
}
