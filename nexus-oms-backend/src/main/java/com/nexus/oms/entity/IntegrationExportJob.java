package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_export_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "flow_id")
    private UUID flowId;

    @NotBlank
    @Column(name = "job_name", nullable = false)
    private String jobName;

    @NotBlank
    @Column(name = "export_type", nullable = false)
    private String exportType;

    @Column(name = "export_config", columnDefinition = "jsonb")
    private String exportConfig;

    @Column(name = "query_criteria", columnDefinition = "jsonb")
    private String queryCriteria;

    @NotBlank
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    private String format;

    private String compression;

    private String encryption;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "record_count")
    private Integer recordCount;

    private String status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (recordCount == null) recordCount = 0;
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
