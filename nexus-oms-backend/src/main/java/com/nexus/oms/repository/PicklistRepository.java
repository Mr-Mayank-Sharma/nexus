package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPicklist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PicklistRepository extends JpaRepository<NxPicklist, UUID> {
    List<NxPicklist> findByTenantId(UUID tenantId);
    List<NxPicklist> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxPicklist> findByAssigneeId(UUID assigneeId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}
