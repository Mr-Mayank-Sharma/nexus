package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_knowledge_bases")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiKnowledgeBase {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @NotBlank @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private String embeddingModel;
    private String chunkingStrategy;
    @Positive private Integer chunkSize;
    @PositiveOrZero private Integer chunkOverlap;
    private String status;
    @PositiveOrZero private Integer documentCount;
    @Positive private Integer vectorSize;
    @Column(columnDefinition = "JSONB") private String config;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
