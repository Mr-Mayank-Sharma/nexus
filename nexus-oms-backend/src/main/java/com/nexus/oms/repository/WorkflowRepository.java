package com.nexus.oms.repository;

import com.nexus.oms.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Page<Workflow> findByTenantId(UUID tenantId, Pageable pageable);

    List<Workflow> findByTenantIdAndCategory(UUID tenantId, String category);

    List<Workflow> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    List<Workflow> findByTenantIdAndStatus(UUID tenantId, String status);
}
