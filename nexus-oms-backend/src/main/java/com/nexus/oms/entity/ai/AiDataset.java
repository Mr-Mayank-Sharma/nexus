package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_datasets")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiDataset {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @NotBlank @Column(nullable = false) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private String datasetType;
    @Column(columnDefinition = "TEXT") private String sourceQuery;
    @PositiveOrZero private Integer recordCount;
    @PositiveOrZero private Long sizeBytes;
    @Column(columnDefinition = "TEXT") private String storageUrl;
    @Column(columnDefinition = "JSONB") private String schemaDef;
    @Column(columnDefinition = "JSONB") private String statistics;
    private String status;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
