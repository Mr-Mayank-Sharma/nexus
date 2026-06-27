package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_rule_fallbacks")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiRuleFallback {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private UUID modelId;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private Integer priority;
    @Column(columnDefinition = "TEXT") private String conditionExpression;
    @Column(columnDefinition = "TEXT") private String actionExpression;
    private String actionType;
    @Column(columnDefinition = "JSONB") private String actionConfig;
    private java.math.BigDecimal confidenceThresholdLow;
    private java.math.BigDecimal confidenceThresholdHigh;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
