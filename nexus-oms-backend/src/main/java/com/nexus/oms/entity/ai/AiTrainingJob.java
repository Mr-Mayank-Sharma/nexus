package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_training_jobs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiTrainingJob {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID modelId;
    private String name;
    private String version;
    private String status;
    private String jobType;
    @Column(columnDefinition = "TEXT") private String triggerReason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String config;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String hyperparameters;
    private UUID trainingDatasetId;
    private UUID validationDatasetId;
    @PositiveOrZero private java.math.BigDecimal accuracy;
    @PositiveOrZero private java.math.BigDecimal precision;
    @PositiveOrZero private java.math.BigDecimal recall;
    @PositiveOrZero @Column(name = "f1_score") private java.math.BigDecimal f1Score;
    @PositiveOrZero private java.math.BigDecimal loss;
    @PositiveOrZero private java.math.BigDecimal driftScore;
    @Column(columnDefinition = "TEXT") private String metricsSource;
    @Positive private Integer epochs;
    @PositiveOrZero private Integer datasetSize;
    @PositiveOrZero private Integer durationSeconds;
    @PositiveOrZero private Long modelSizeBytes;
    @Column(columnDefinition = "TEXT") private String errorMessage;
    @Column(columnDefinition = "TEXT") private String logs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
