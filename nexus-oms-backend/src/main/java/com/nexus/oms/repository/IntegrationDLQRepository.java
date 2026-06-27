package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationDLQ;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationDLQRepository extends JpaRepository<IntegrationDLQ, UUID> {

    Page<IntegrationDLQ> findByTenantId(UUID tenantId, Pageable pageable);

    List<IntegrationDLQ> findByFlowId(UUID flowId);

    List<IntegrationDLQ> findByStatus(String status);

    Page<IntegrationDLQ> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
