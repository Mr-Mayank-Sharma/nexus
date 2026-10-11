package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** What was calculated for one SKU at one channel location, from which nodes, and what happened. */
@Entity
@Table(name = "nx_inventory_publish_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxInventoryPublishLine {

    public static final String PLANNED = "PLANNED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String UNCHANGED = "UNCHANGED";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private String sku;

    @Column(name = "external_location_id", nullable = false)
    private String externalLocationId;

    @Column(name = "inventory_item_id")
    private Long inventoryItemId;

    @Column(name = "calculated_qty", nullable = false)
    private Integer calculatedQty;

    /** The quantity the channel held when we looked, before any change. */
    @Column(name = "channel_qty_before")
    private Integer channelQtyBefore;

    @Column(name = "published_qty")
    private Integer publishedQty;

    @Column(nullable = false, length = 16)
    private String outcome;

    /** The channel's quantity differed from what Nexus last published (changed outside Nexus). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean drift = false;

    @Column(columnDefinition = "TEXT")
    private String calculation;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
