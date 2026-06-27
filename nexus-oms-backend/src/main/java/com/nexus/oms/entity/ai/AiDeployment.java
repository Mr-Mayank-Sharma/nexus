package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_deployments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiDeployment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID modelId;
    @Column(nullable = false) private UUID versionId;
    private String environment;
    private java.math.BigDecimal trafficWeight;
    @Column(columnDefinition = "TEXT") private String endpointUrl;
    @Column(columnDefinition = "JSONB") private String configOverrides;
    private String status;
    private String deployedBy;
    private LocalDateTime deployedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); deployedAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
