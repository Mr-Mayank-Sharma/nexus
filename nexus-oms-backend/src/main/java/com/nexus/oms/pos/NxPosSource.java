package com.nexus.oms.pos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_pos_sources")
@Getter
@Setter
@NoArgsConstructor
public class NxPosSource {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String vendor = "CUSTOM";

    /** SHA-256 of the shared secret; the secret itself is shown once at creation and never stored. */
    @JsonIgnore
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "stale_after_minutes", nullable = false)
    private Integer staleAfterMinutes = 120;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_snapshot_at")
    private LocalDateTime lastSnapshotAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
