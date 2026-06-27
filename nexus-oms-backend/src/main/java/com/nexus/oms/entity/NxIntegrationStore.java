package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_stores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxIntegrationStore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "store_code", nullable = false)
    private String storeCode;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(nullable = false)
    private String platform;

    @Column(name = "platform_type")
    private String platformType;

    private String status;

    private String currency;

    @Column(name = "default_locale")
    private String defaultLocale;

    private String timezone;

    @Column(name = "external_store_id")
    private String externalStoreId;

    @Column(name = "external_domain")
    private String externalDomain;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (currency == null) currency = "USD";
        if (defaultLocale == null) defaultLocale = "en_US";
        if (timezone == null) timezone = "UTC";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
