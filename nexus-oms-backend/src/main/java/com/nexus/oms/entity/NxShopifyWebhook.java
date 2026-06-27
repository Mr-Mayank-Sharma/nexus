package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_shopify_webhooks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxShopifyWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "shopify_webhook_id", nullable = false)
    private Long shopifyWebhookId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String address;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }
}
