package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_feature_values")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiFeatureValue {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID featureId;
    @Column(nullable = false) private String entityId;
    private String entityType;
    @Column(columnDefinition = "TEXT") private String value;
    private java.math.BigDecimal numericValue;
    private Boolean boolValue;
    @Column(columnDefinition = "JSONB") private String jsonValue;
    private LocalDateTime timestampValue;
    @Column(nullable = false) private LocalDate asOfDate;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
