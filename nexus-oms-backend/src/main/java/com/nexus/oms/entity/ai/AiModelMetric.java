package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_model_metrics")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelMetric {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID modelId;
    private UUID versionId;
    @NotBlank @Column(nullable = false) private String metricName;
    @NotNull @PositiveOrZero @Column(nullable = false) private java.math.BigDecimal metricValue;
    private LocalDateTime recordedAt;
    @Column(columnDefinition = "JSONB") private String metadata;
    @PrePersist protected void onCreate() { recordedAt = LocalDateTime.now(); }
}
