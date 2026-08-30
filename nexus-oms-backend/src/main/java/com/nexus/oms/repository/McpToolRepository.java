package com.nexus.oms.repository;

import com.nexus.oms.entity.NxMcpTool;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpToolRepository extends JpaRepository<NxMcpTool, UUID> {

    List<NxMcpTool> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<NxMcpTool> findByTenantId(UUID tenantId);

    Optional<NxMcpTool> findByTenantIdAndToolName(UUID tenantId, String toolName);
}
