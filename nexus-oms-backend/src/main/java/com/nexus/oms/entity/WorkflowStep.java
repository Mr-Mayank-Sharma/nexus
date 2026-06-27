package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_workflow_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "step_order")
    private Integer stepOrder;

    @Column(name = "step_type")
    private String stepType;

    @Column(name = "step_name")
    private String stepName;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "condition_expression")
    private String conditionExpression;

    @Column(name = "on_failure")
    private String onFailure;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
