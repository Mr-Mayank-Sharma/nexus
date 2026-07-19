package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_validation_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationValidationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @NotBlank
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "field_path")
    private String fieldPath;

    private String operator;

    @Column(name = "\"value\"", columnDefinition = "text")
    private String value;

    @Column(name = "error_message")
    private String errorMessage;

    private String severity;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (severity == null) severity = "ERROR";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
