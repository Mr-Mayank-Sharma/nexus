package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_model_metrics")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelMetric {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private UUID modelId;
    private UUID versionId;
    @Column(nullable = false) private String metricName;
    @Column(nullable = false) private java.math.BigDecimal metricValue;
    private LocalDateTime recordedAt;
    @Column(columnDefinition = "JSONB") private String metadata;
    @PrePersist protected void onCreate() { recordedAt = LocalDateTime.now(); }
}
