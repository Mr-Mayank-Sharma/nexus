package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderAllocation;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OrderAllocationRepository extends JpaRepository<NxOrderAllocation, UUID> {
    List<NxOrderAllocation> findByOrderId(UUID orderId);
    List<NxOrderAllocation> findByTenantId(UUID tenantId, Sort sort);
    List<NxOrderAllocation> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxOrderAllocation> findByNodeId(UUID nodeId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByTenantId(UUID tenantId);
}
