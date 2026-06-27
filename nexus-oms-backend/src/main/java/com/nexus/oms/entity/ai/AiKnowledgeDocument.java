package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_knowledge_documents")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiKnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID knowledgeBaseId;
    private UUID tenantId;
    private String title;
    @Column(columnDefinition = "TEXT") private String content;
    private String contentType;
    @Column(columnDefinition = "TEXT") private String sourceUrl;
    @Column(columnDefinition = "TEXT") private String filePath;
    private Long fileSizeBytes;
    private Integer chunkCount;
    private String embeddingStatus;
    @Column(columnDefinition = "JSONB") private String metadata;
    private String checksum;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
