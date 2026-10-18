package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderAllocation;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderAllocationRepository extends JpaRepository<NxOrderAllocation, UUID> {
    List<NxOrderAllocation> findByOrderId(UUID orderId);

    List<NxOrderAllocation> findByTenantIdAndOrderIdIn(UUID tenantId, java.util.Collection<UUID> orderIds);
    List<NxOrderAllocation> findByTenantId(UUID tenantId, Sort sort);
    List<NxOrderAllocation> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxOrderAllocation> findByNodeId(UUID nodeId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByTenantId(UUID tenantId);

    /**
     * Units allocated per store since a date: [nodeId, units]. Re-routed allocations are CANCELLED rows and
     * still count, because the store was given those units to pick.
     */
    @Query("SELECT a.nodeId, SUM(a.quantityAllocated) FROM NxOrderAllocation a WHERE a.tenantId = :tenantId " +
           "AND a.nodeId IS NOT NULL AND a.quantityAllocated > 0 AND a.allocatedAt >= :since GROUP BY a.nodeId")
    List<Object[]> allocatedUnitsByNode(@Param("tenantId") UUID tenantId, @Param("since") LocalDateTime since);
}
