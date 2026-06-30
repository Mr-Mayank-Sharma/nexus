package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_flows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @NotBlank
    @Column(name = "flow_type", nullable = false)
    private String flowType;

    private String status;

    @Column(name = "source_endpoint_id")
    private UUID sourceEndpointId;

    @Column(name = "target_endpoint_id")
    private UUID targetEndpointId;

    @NotBlank
    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "trigger_config", columnDefinition = "jsonb")
    private String triggerConfig;

    @Column(name = "schedule_cron")
    private String scheduleCron;

    private Integer priority;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "retry_delay_seconds")
    private Integer retryDelaySeconds;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "throttle_rate")
    private Integer throttleRate;

    @Column(name = "processing_timeout_minutes")
    private Integer processingTimeoutMinutes;

    @Column(name = "error_handling")
    private String errorHandling;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
        if (priority == null) priority = 5;
        if (maxRetries == null) maxRetries = 3;
        if (retryDelaySeconds == null) retryDelaySeconds = 60;
        if (batchSize == null) batchSize = 1000;
        if (throttleRate == null) throttleRate = 0;
        if (processingTimeoutMinutes == null) processingTimeoutMinutes = 60;
        if (errorHandling == null) errorHandling = "STOP";
        if (isActive == null) isActive = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
