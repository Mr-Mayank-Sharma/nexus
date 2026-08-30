package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-08: RFID scan session — batches EPC reads from a wand, dedups in-session.
 */
@Entity
@Table(name = "nx_rfid_scan_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxRfidScanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "session_type", nullable = false, length = 20)
    private String sessionType;

    @Column(length = 20)
    private String mode;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(length = 20)
    private String status;

    @Column(name = "seen_epcs", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String seenEpcs;

    @Column(name = "rejected_epcs", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rejectedEpcs;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
        if (status == null) status = "OPEN";
        if (mode == null) mode = "FULL_REGISTRY";
    }
}
