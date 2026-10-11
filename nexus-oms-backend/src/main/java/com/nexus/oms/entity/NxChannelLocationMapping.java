package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Links a Nexus node (warehouse or store) to the sales-channel location its stock is published to. */
@Entity
@Table(name = "nx_channel_location_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxChannelLocationMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "integration_store_id", nullable = false)
    private UUID integrationStoreId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "external_location_id", nullable = false)
    private String externalLocationId;

    @Column(name = "external_location_name")
    private String externalLocationName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** Units held back from the channel for this node on top of its own safety stock. */
    @Column(name = "buffer_units", nullable = false)
    @Builder.Default
    private Integer bufferUnits = 0;

    /** Most this node may contribute per SKU; null means no cap. */
    @Column(name = "contribution_cap")
    private Integer contributionCap;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
