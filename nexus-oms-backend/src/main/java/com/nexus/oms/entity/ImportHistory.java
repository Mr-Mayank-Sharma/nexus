package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "import_type", nullable = false, length = 50)
    private String importType;

    @Column(name = "file_format", nullable = false, length = 10)
    private String fileFormat;

    @Column(name = "import_mode", nullable = false, length = 30)
    @Builder.Default
    private String importMode = "CONTINUE_ON_ERROR";

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_records", nullable = false)
    @Builder.Default
    private int totalRecords = 0;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private int successCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private int duplicateCount = 0;

    @Column(name = "processing_time_ms", nullable = false)
    @Builder.Default
    private long processingTimeMs = 0;

    @Column(name = "file_size_bytes", nullable = false)
    @Builder.Default
    private long fileSizeBytes = 0;

    @Column(name = "stored_file_path", length = 1000)
    private String storedFilePath;

    @Column(name = "processed_file_path", length = 1000)
    private String processedFilePath;

    @Column(name = "error_file_path", length = 1000)
    private String errorFilePath;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
