package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** The plan a tenant has bought. Absent row = SUITE with every add-on (pre-licensing default). */
@Entity
@Table(name = "nx_tenant_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxTenantSubscription {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 16)
    private String edition;

    /** Comma-separated add-on modules, e.g. "THREE_PL,EDI". */
    @Column(name = "add_ons")
    private String addOns;

    /** After this instant only PLATFORM stays available (read access to own data). */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
