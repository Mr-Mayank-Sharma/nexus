package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_experiments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiExperiment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(nullable = false) private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID modelId;
    @NotBlank @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private String experimentType;
    private UUID championVersionId;
    private UUID challengerVersionId;
    @PositiveOrZero private java.math.BigDecimal trafficSplit;
    private String successMetric;
    private String status;
    private UUID winnerVersionId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    @Column(columnDefinition = "JSONB") private String results;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
