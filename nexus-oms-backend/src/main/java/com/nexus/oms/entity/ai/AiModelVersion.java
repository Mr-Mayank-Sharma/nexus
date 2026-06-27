package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_model_versions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelVersion {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID modelId;
    @Column(nullable = false) private String version;
    @Column(columnDefinition = "TEXT") private String modelFileUrl;
    private Long modelSizeBytes;
    private String framework;
    private String frameworkVersion;
    private java.math.BigDecimal accuracy;
    private java.math.BigDecimal precision;
    private java.math.BigDecimal recall;
    private java.math.BigDecimal f1Score;
    private java.math.BigDecimal latencyMs;
    private UUID trainingDatasetId;
    private UUID validationDatasetId;
    private UUID testDatasetId;
    private UUID trainingJobId;
    @Column(columnDefinition = "JSONB") private String metrics;
    @Column(columnDefinition = "JSONB") private String parameters;
    @Column(columnDefinition = "TEXT") private String commitMessage;
    private String status;
    private String validatedBy;
    private LocalDateTime validatedAt;
    private String deployedBy;
    private LocalDateTime deployedAt;
    private String createdBy;
    private LocalDateTime createdAt;
}
