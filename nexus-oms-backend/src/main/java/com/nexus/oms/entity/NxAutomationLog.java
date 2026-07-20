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
@Table(name = "nx_automation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAutomationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotNull
    @Column(name = "system_id", nullable = false)
    private UUID systemId;

    @Column(name = "command_id")
    private UUID commandId;

    @NotBlank
    @Column(name = "log_level", nullable = false)
    private String logLevel;

    @NotBlank
    @Column(nullable = false)
    private String event;

    private String message;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String data;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (logLevel == null) logLevel = "INFO";
    }
}
