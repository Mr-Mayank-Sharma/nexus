package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_inference_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiInferenceLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(nullable = false) private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID modelId;
    private UUID versionId;
    private UUID deploymentId;
    private String requestId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String inputData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String outputData;
    @PositiveOrZero private java.math.BigDecimal confidence;
    @PositiveOrZero private java.math.BigDecimal latencyMs;
    private String status;
    private Boolean fallbackUsed;
    private String fallbackReason;
    private Boolean ruleEngineUsed;
    private Boolean userOverridden;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String userOverrideValue;
    private String userId;
    private String sourceService;
    @PositiveOrZero private java.math.BigDecimal cost;
    @PositiveOrZero private Integer tokensUsed;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
