package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    private String artifactFormat;
    private String artifactChecksum;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String featureColumns;
    private String calibrationType;
    @PositiveOrZero private java.math.BigDecimal accuracy;
    @PositiveOrZero private java.math.BigDecimal precision;
    @PositiveOrZero private java.math.BigDecimal recall;
    @PositiveOrZero @Column(name = "f1_score") private java.math.BigDecimal f1Score;
    @PositiveOrZero private java.math.BigDecimal latencyMs;
    private UUID trainingDatasetId;
    private UUID validationDatasetId;
    private UUID testDatasetId;
    private UUID trainingJobId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String metrics;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String parameters;
    @Column(columnDefinition = "TEXT") private String commitMessage;
    private String status;
    private String validatedBy;
    private LocalDateTime validatedAt;
    private Boolean gateOverride;
    @Column(columnDefinition = "TEXT") private String gateFailures;
    private String deployedBy;
    private LocalDateTime deployedAt;
    private String createdBy;
    private LocalDateTime createdAt;
}
