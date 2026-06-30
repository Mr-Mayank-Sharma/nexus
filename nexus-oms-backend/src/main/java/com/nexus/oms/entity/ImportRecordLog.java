package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_record_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRecordLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "import_history_id", nullable = false)
    private UUID importHistoryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "suggested_resolution", columnDefinition = "TEXT")
    private String suggestedResolution;

    @Column(name = "original_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String originalData;

    @Column(name = "processed_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String processedData;

    @Column(length = 50)
    private String stage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
