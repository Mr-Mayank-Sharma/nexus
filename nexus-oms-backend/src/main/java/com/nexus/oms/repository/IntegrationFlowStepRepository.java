package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationFlowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationFlowStepRepository extends JpaRepository<IntegrationFlowStep, UUID> {

    List<IntegrationFlowStep> findByFlowId(UUID flowId);

    List<IntegrationFlowStep> findByFlowIdOrderByStepOrderAsc(UUID flowId);
}
