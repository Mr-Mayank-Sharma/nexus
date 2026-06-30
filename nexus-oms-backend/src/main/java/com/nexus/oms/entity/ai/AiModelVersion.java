package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_model_versions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelVersion {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(nullable = false) private UUID modelId;
    @NotBlank @Column(nullable = false) private String version;
    @Column(columnDefinition = "TEXT") private String modelFileUrl;
    @PositiveOrZero private Long modelSizeBytes;
    private String framework;
    private String frameworkVersion;
    @PositiveOrZero private java.math.BigDecimal accuracy;
    @PositiveOrZero private java.math.BigDecimal precision;
    @PositiveOrZero private java.math.BigDecimal recall;
    @PositiveOrZero private java.math.BigDecimal f1Score;
    @PositiveOrZero private java.math.BigDecimal latencyMs;
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
