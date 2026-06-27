package com.nexus.oms.repository;

import com.nexus.oms.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, UUID> {

    List<WorkflowStep> findByWorkflowId(UUID workflowId);

    List<WorkflowStep> findByWorkflowIdOrderByStepOrderAsc(UUID workflowId);
}
