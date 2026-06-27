package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_bigcommerce_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxBigCommerceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String storeHash;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "api_path")
    private String apiPath;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "auto_sync_orders")
    private Boolean autoSyncOrders;

    @Column(name = "auto_sync_inventory")
    private Boolean autoSyncInventory;

    @Column(name = "sync_interval_minutes")
    private Integer syncIntervalMinutes;

    @Column(name = "last_order_sync_at")
    private LocalDateTime lastOrderSyncAt;

    @Column(name = "last_product_sync_at")
    private LocalDateTime lastProductSyncAt;

    @Column(name = "last_inventory_sync_at")
    private LocalDateTime lastInventorySyncAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (apiPath == null) apiPath = "https://api.bigcommerce.com";
        if (syncIntervalMinutes == null) syncIntervalMinutes = 15;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
