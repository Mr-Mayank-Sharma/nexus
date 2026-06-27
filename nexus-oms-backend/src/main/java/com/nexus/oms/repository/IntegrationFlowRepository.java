package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationFlow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationFlowRepository extends JpaRepository<IntegrationFlow, UUID> {

    Page<IntegrationFlow> findByTenantId(UUID tenantId, Pageable pageable);

    Page<IntegrationFlow> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<IntegrationFlow> findByTenantIdAndFlowType(UUID tenantId, String flowType, Pageable pageable);

    Page<IntegrationFlow> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive, Pageable pageable);

    List<IntegrationFlow> findBySourceEndpointIdOrTargetEndpointId(UUID sourceEndpointId, UUID targetEndpointId);
}
