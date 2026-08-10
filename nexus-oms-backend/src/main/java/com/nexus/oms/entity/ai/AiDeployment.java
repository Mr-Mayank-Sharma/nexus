package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_deployments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiDeployment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(nullable = false) private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID modelId;
    @NotNull @Column(nullable = false) private UUID versionId;
    private String environment;
    @PositiveOrZero private java.math.BigDecimal trafficWeight;
    @Column(columnDefinition = "TEXT") private String endpointUrl;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String configOverrides;
    private String status;
    private String deployedBy;
    private LocalDateTime deployedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); deployedAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
