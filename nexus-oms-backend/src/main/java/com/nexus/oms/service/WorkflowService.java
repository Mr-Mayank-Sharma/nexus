package com.nexus.oms.service;

import com.nexus.oms.entity.Workflow;
import com.nexus.oms.entity.WorkflowExecution;
import com.nexus.oms.entity.WorkflowStep;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.WorkflowExecutionRepository;
import com.nexus.oms.repository.WorkflowRepository;
import com.nexus.oms.repository.WorkflowStepRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;

    public WorkflowService(WorkflowRepository workflowRepository,
                           WorkflowStepRepository workflowStepRepository,
                           WorkflowExecutionRepository workflowExecutionRepository) {
        this.workflowRepository = workflowRepository;
        this.workflowStepRepository = workflowStepRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
    }

    public Page<Workflow> getAllWorkflows(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return workflowRepository.findByTenantId(tenantId, pageable);
    }

    public Workflow getWorkflow(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return workflowRepository.findById(id)
                .filter(w -> w.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
    }

    @Transactional
    public Workflow createWorkflow(Workflow w, List<WorkflowStep> steps) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        w.setTenantId(tenantId);
        Workflow saved = workflowRepository.save(w);
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                WorkflowStep step = steps.get(i);
                step.setWorkflowId(saved.getId());
                step.setStepOrder(i + 1);
                workflowStepRepository.save(step);
            }
        }
        return saved;
    }

    @Transactional
    public Workflow updateWorkflowStatus(UUID id, String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Workflow w = workflowRepository.findById(id)
                .filter(wf -> wf.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
        w.setStatus(status);
        return workflowRepository.save(w);
    }

    @Transactional
    public Workflow toggleWorkflow(UUID id, boolean active) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Workflow w = workflowRepository.findById(id)
                .filter(wf -> wf.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
        w.setIsActive(active);
        return workflowRepository.save(w);
    }

    public List<WorkflowStep> getWorkflowSteps(UUID workflowId) {
        return workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);
    }

    @Transactional
    public WorkflowExecution executeWorkflow(UUID workflowId, String entityType, String entityId,
                                             Map<String, Object> inputData) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        workflowRepository.findById(workflowId)
                .filter(wf -> wf.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", workflowId));

        List<WorkflowStep> steps = workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);

        LocalDateTime startedAt = LocalDateTime.now();

        WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId(tenantId)
                .workflowId(workflowId)
                .triggerEntityType(entityType)
                .triggerEntityId(UUID.fromString(entityId))
                .status("RUNNING")
                .totalSteps(steps.size())
                .inputData(inputData != null ? inputData.toString() : null)
                .startedAt(startedAt)
                .build();
        execution = workflowExecutionRepository.save(execution);

        for (WorkflowStep step : steps) {
            execution.setCurrentStep(step.getStepName());
            workflowExecutionRepository.save(execution);
        }

        LocalDateTime completedAt = LocalDateTime.now();
        long durationMs = Duration.between(startedAt, completedAt).toMillis();

        execution.setStatus("COMPLETED");
        execution.setCompletedAt(completedAt);
        execution.setDurationMs(durationMs);
        return workflowExecutionRepository.save(execution);
    }

    public List<WorkflowExecution> getExecutions(UUID workflowId) {
        return workflowExecutionRepository.findByWorkflowId(workflowId);
    }
}
