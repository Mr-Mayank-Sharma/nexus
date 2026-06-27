package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_inference_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiInferenceLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID modelId;
    private UUID versionId;
    private UUID deploymentId;
    private String requestId;
    @Column(columnDefinition = "JSONB") private String inputData;
    @Column(columnDefinition = "JSONB") private String outputData;
    private java.math.BigDecimal confidence;
    private java.math.BigDecimal latencyMs;
    private String status;
    private Boolean fallbackUsed;
    private String fallbackReason;
    private Boolean ruleEngineUsed;
    private Boolean userOverridden;
    @Column(columnDefinition = "JSONB") private String userOverrideValue;
    private String userId;
    private String sourceService;
    private java.math.BigDecimal cost;
    private Integer tokensUsed;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
