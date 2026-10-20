package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_automation_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAutomationAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "system_id", nullable = false)
    private UUID systemId;

    @NotBlank
    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @NotBlank
    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String status;

    @NotBlank
    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "current_value")
    private Double currentValue;

    private String unit;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "auto_resolve")
    private Boolean autoResolve;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
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
        if (autoResolve == null) autoResolve = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
