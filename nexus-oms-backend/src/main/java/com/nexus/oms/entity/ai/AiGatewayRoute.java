package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_gateway_routes")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiGatewayRoute {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String modelType;
    private String routePattern;
    @Column(columnDefinition = "TEXT") private String targetEndpoint;
    private String fallbackStrategy;
    private UUID fallbackModelId;
    private Integer rateLimitPerMinute;
    private Integer timeoutMs;
    private Integer retryCount;
    private Boolean isActive;
    @Column(columnDefinition = "JSONB") private String metadata;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
