package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationExportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationExportJobRepository extends JpaRepository<IntegrationExportJob, UUID> {

    Page<IntegrationExportJob> findByTenantId(UUID tenantId, Pageable pageable);

    List<IntegrationExportJob> findByFlowId(UUID flowId);

    Page<IntegrationExportJob> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
