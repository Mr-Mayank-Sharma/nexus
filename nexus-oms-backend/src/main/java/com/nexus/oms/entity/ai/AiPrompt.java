package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_prompts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiPrompt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private UUID modelId;
    @Column(nullable = false, columnDefinition = "TEXT") private String promptTemplate;
    @Column(columnDefinition = "JSONB") private String variables;
    @Column(columnDefinition = "JSONB") private String responseSchema;
    private java.math.BigDecimal temperature;
    private Integer maxTokens;
    private Integer version;
    private String status;
    private String tags;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
