package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-13: Field-level sync mapping control.
 *
 * Defines which side (LOCAL or REMOTE) wins for a given field on a given
 * entity type and direction. Defaults to LOCAL_WINS (preserve newer local
 * state) when no mapping exists.
 */
@Entity
@Table(name = "nx_sync_field_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSyncFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(nullable = false, length = 20)
    private String winner;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }
}
