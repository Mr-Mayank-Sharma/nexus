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
@Table(name = "nx_automation_commands")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAutomationCommand {

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
    @Column(name = "command_type", nullable = false)
    private String commandType;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String parameters;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "picklist_id")
    private UUID picklistId;

    @Column(name = "wave_id")
    private UUID waveId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

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
        if (status == null) status = "PENDING";
        if (priority == null) priority = 0;
        if (timeoutMs == null) timeoutMs = 30000;
        if (retryCount == null) retryCount = 0;
        if (maxRetries == null) maxRetries = 3;
        if (assignedBy == null) assignedBy = "USER";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
