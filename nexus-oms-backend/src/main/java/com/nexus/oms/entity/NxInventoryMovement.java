package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** One immutable line of the inventory ledger: what changed on a stock row, why, and for which document. */
@Entity
@Table(name = "nx_inventory_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxInventoryMovement {

    public static final String RECEIPT = "RECEIPT";
    public static final String SHIPMENT = "SHIPMENT";
    public static final String ADJUSTMENT = "ADJUSTMENT";
    public static final String CYCLE_COUNT = "CYCLE_COUNT";
    public static final String RESERVE = "RESERVE";
    public static final String RELEASE = "RELEASE";
    public static final String RETURN = "RETURN";
    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TRANSFER_IN = "TRANSFER_IN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "on_hand_delta", nullable = false)
    private Integer onHandDelta;

    @Column(name = "allocated_delta", nullable = false)
    private Integer allocatedDelta;

    @Column(name = "reserved_delta", nullable = false)
    private Integer reservedDelta;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (onHandDelta == null) onHandDelta = 0;
        if (allocatedDelta == null) allocatedDelta = 0;
        if (reservedDelta == null) reservedDelta = 0;
    }
}
