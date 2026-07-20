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
@Table(name = "nx_automation_systems")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAutomationSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @NotBlank
    @Column(name = "system_name", nullable = false)
    private String systemName;

    @NotBlank
    @Column(name = "system_type", nullable = false)
    private String systemType;

    private String vendor;

    private String model;

    @Column(name = "protocol")
    private String protocol;

    @Column(name = "endpoint_url")
    private String endpointUrl;

    @Column(name = "api_key")
    private String apiKey;

    @Column(nullable = false)
    private String status;

    @Column(name = "health_check_url")
    private String healthCheckUrl;

    @Column(name = "last_health_check_at")
    private LocalDateTime lastHealthCheckAt;

    @Column(name = "health_check_interval_sec")
    private Integer healthCheckIntervalSec;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String capabilities;

    @Column(name = "connection_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String connectionConfig;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    @Column(name = "error_message")
    private String errorMessage;

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
        if (status == null) status = "OFFLINE";
        if (isActive == null) isActive = true;
        if (healthCheckIntervalSec == null) healthCheckIntervalSec = 30;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
