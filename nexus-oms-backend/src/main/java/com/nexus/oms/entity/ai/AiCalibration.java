package com.nexus.oms.entity.ai;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_calibrations")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AiCalibration {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @NotNull @Column(nullable = false) private UUID tenantId;
    @NotNull @Column(nullable = false) private UUID modelId;
    private UUID versionId;
    @NotBlank @Column(nullable = false) private String entityId;
    private String entityType;
    @PositiveOrZero private BigDecimal scaleFactor;
    private BigDecimal bias;
    @PositiveOrZero private Integer sampleCount;
    @PositiveOrZero private BigDecimal weight;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
