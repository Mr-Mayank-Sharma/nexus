package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_models")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModel {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private String name;
    private String displayName;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(nullable = false) private String modelType;
    @Column(nullable = false) private String category;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "base_model_id") private AiModel baseModel;
    private String status;
    private String currentVersion;
    @Column(columnDefinition = "JSONB") private String inputSchema;
    @Column(columnDefinition = "JSONB") private String outputSchema;
    @Column(columnDefinition = "JSONB") private String config;
    private String tags;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
