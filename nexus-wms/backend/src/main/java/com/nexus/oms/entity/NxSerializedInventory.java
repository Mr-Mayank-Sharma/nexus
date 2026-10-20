package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-08: Serialized inventory (EPC registry) for RFID.
 *
 * One row per unique EPC (serialized tag). Supports two modes:
 *   FULL_REGISTRY  = one-for-one serialized tracking
 *   DECODE_TO_UPC  = traditional quantity uptick (EPC decoded to UPC)
 */
@Entity
@Table(name = "nx_serialized_inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSerializedInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false, length = 128)
    private String epc;

    @Column(length = 100)
    private String sku;

    @Column(length = 20)
    private String status;

    @Column(length = 20)
    private String mode;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (mode == null) mode = "FULL_REGISTRY";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
