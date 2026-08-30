package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-13: A detected bidirectional-sync conflict.
 *
 * When an inbound update arrives with a base version older than the local
 * version, the update is a conflict and is recorded here rather than silently
 * overwriting newer local state.
 */
@Entity
@Table(name = "nx_sync_conflicts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxSyncConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(name = "source_system", nullable = false, length = 50)
    private String sourceSystem;

    @Column(name = "inbound_version")
    private Long inboundVersion;

    @Column(name = "local_version")
    private Long localVersion;

    @Column(name = "conflicting_fields", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String conflictingFields;

    @Column(length = 20)
    private String resolution;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (resolution == null) resolution = "PENDING";
        if (status == null) status = "OPEN";
    }
}
