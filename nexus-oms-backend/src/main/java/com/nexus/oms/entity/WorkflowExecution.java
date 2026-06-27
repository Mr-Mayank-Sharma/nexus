package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_workflow_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "trigger_entity_type")
    private String triggerEntityType;

    @Column(name = "trigger_entity_id")
    private UUID triggerEntityId;

    private String status;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "total_steps")
    private Integer totalSteps;

    @Column(name = "input_data", columnDefinition = "jsonb")
    private String inputData;

    @Column(name = "output_data", columnDefinition = "jsonb")
    private String outputData;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
