package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_feature_definitions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiFeatureDefinition {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @NotBlank @Column(nullable = false) private String name;
    private String displayName;
    @Column(columnDefinition = "TEXT") private String description;
    @NotBlank @Column(nullable = false) private String featureGroup;
    @NotBlank @Column(nullable = false) private String dataType;
    private String entityType;
    private String sourceType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String sourceConfig;
    @Column(columnDefinition = "TEXT") private String transformationSql;
    private Boolean isCategorical;
    @PositiveOrZero private Integer cardinality;
    private String defaultValue;
    private Boolean isActive;
    private Integer version;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String metadata;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
