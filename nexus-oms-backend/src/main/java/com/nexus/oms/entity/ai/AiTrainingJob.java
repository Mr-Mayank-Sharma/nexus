package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_training_jobs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiTrainingJob {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private UUID modelId;
    private String name;
    private String version;
    private String status;
    private String jobType;
    @Column(columnDefinition = "TEXT") private String triggerReason;
    @Column(columnDefinition = "JSONB") private String config;
    @Column(columnDefinition = "JSONB") private String hyperparameters;
    private UUID trainingDatasetId;
    private UUID validationDatasetId;
    private java.math.BigDecimal accuracy;
    private java.math.BigDecimal precision;
    private java.math.BigDecimal recall;
    private java.math.BigDecimal f1Score;
    private java.math.BigDecimal loss;
    private java.math.BigDecimal driftScore;
    private Integer epochs;
    private Integer datasetSize;
    private Integer durationSeconds;
    private Long modelSizeBytes;
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
