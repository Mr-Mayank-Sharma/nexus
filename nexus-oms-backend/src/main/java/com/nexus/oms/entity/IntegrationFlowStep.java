package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_flow_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationFlowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    @Column(name = "step_order")
    private Integer stepOrder;

    @NotBlank
    @Column(name = "step_type", nullable = false)
    private String stepType;

    @Column(name = "transformer_type")
    private String transformerType;

    @NotBlank
    @Column(columnDefinition = "jsonb", nullable = false)
    private String config;

    @Column(name = "condition_expression", columnDefinition = "text")
    private String conditionExpression;

    @Column(name = "on_error")
    private String onError;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (onError == null) onError = "STOP";
        if (timeoutSeconds == null) timeoutSeconds = 300;
    }
}
