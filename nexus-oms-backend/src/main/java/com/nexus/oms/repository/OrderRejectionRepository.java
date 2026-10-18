package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderRejection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRejectionRepository extends JpaRepository<NxOrderRejection, UUID> {
    List<NxOrderRejection> findByTenantId(UUID tenantId);
    List<NxOrderRejection> findByOrderId(UUID orderId);
    List<NxOrderRejection> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxOrderRejection> findByTenantIdAndRejectionCode(UUID tenantId, String rejectionCode);
    List<NxOrderRejection> findByOrderItemId(UUID orderItemId);

    /** Units rejected per store since a date: [nodeId, units]. Cancelled rejections do not count. */
    @Query("SELECT r.nodeId, SUM(r.quantity) FROM NxOrderRejection r WHERE r.tenantId = :tenantId " +
           "AND r.nodeId IS NOT NULL AND r.createdAt >= :since AND r.status <> 'CANCELLED' GROUP BY r.nodeId")
    List<Object[]> rejectedUnitsByNode(@Param("tenantId") UUID tenantId, @Param("since") LocalDateTime since);
}
