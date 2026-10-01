package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_fulfillment_reconciliation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxFulfillmentReconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "shopify_fulfillment_id")
    private String shopifyFulfillmentId;

    @Column(name = "match_type", nullable = false)
    private String matchType;

    @Column(name = "fulfilled_location")
    private String fulfilledLocation;

    @Column(name = "routed_location")
    private String routedLocation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fulfilled_items", columnDefinition = "jsonb")
    private String fulfilledItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "remaining_items", columnDefinition = "jsonb")
    private String remainingItems;

    @Column(name = "resolution", nullable = false)
    private String resolution;

    @Column(name = "netsuite_sync_status")
    private String netsuiteSyncStatus;

    @Column(name = "inventory_delta")
    private Integer inventoryDelta;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (netsuiteSyncStatus == null) {
            netsuiteSyncStatus = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}