package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_models")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModel {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @NotBlank @Column(nullable = false) private String name;
    private String displayName;
    @Column(columnDefinition = "TEXT") private String description;
    @NotBlank @Column(nullable = false) private String modelType;
    @NotBlank @Column(nullable = false) private String category;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "base_model_id") private AiModel baseModel;
    private String status;
    private String currentVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String inputSchema;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String outputSchema;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String config;
    private String tags;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
