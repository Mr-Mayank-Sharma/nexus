package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_feature_values")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiFeatureValue {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(nullable = false) private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID featureId;
    @NotBlank @Column(nullable = false) private String entityId;
    private String entityType;
    @Column(columnDefinition = "TEXT") private String value;
    private java.math.BigDecimal numericValue;
    private Boolean boolValue;
    @Column(columnDefinition = "JSONB") private String jsonValue;
    private LocalDateTime timestampValue;
    @NotNull @Column(nullable = false) private LocalDate asOfDate;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
