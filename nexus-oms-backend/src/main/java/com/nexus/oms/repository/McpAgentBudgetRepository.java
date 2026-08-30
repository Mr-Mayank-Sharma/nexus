package com.nexus.oms.repository;

import com.nexus.oms.entity.NxMcpAgentBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpAgentBudgetRepository extends JpaRepository<NxMcpAgentBudget, UUID> {

    List<NxMcpAgentBudget> findByTenantId(UUID tenantId);

    Optional<NxMcpAgentBudget> findByTenantIdAndAgentName(UUID tenantId, String agentName);
}
