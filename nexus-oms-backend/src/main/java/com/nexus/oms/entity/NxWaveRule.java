package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_wave_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxWaveRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "wave_id", nullable = false)
    private UUID waveId;

    @NotNull
    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @NotNull
    @Column(nullable = false)
    private String operator;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (sequence == null) sequence = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
