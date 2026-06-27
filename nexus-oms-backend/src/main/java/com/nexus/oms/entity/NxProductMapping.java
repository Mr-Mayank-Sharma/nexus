package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_product_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "bc_product_id", nullable = false)
    private Integer bcProductId;

    @Column(name = "bc_variant_id")
    private Integer bcVariantId;

    @Column(name = "bc_sku")
    private String bcSku;

    @Column(name = "nexus_sku", nullable = false)
    private String nexusSku;

    @Column(name = "nexus_product_name")
    private String nexusProductName;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
