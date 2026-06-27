package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationImportJobRepository extends JpaRepository<IntegrationImportJob, UUID> {

    Page<IntegrationImportJob> findByTenantId(UUID tenantId, Pageable pageable);

    List<IntegrationImportJob> findByFlowId(UUID flowId);

    Page<IntegrationImportJob> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
