package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_compute_resources")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiComputeResource {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    private UUID tenantId;
    @Column(nullable = false) private String name;
    private String resourceType;
    private String provider;
    @Column(columnDefinition = "JSONB") private String config;
    private java.math.BigDecimal allocatedUnits;
    private java.math.BigDecimal usedUnits;
    private java.math.BigDecimal costPerUnit;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
