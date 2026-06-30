package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_import_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationImportJob {

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
    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_config", columnDefinition = "jsonb")
    private String sourceConfig;

    @NotBlank
    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_config", columnDefinition = "jsonb")
    private String targetConfig;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "error_count")
    private Integer errorCount;

    private String status;

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

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
        if (successCount == null) successCount = 0;
        if (errorCount == null) errorCount = 0;
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
