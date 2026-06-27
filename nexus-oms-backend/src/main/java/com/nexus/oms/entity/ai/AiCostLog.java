package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "ai_cost_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiCostLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID modelId;
    private UUID versionId;
    private String costType;
    @Column(nullable = false) private java.math.BigDecimal amount;
    private String currency;
    private Integer tokensUsed;
    private java.math.BigDecimal computeHours;
    private LocalDateTime recordedAt;
    @Column(columnDefinition = "TEXT") private String description;
    @PrePersist protected void onCreate() { recordedAt = LocalDateTime.now(); }
}
