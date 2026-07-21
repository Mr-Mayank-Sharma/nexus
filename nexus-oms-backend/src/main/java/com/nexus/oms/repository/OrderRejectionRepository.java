package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderRejection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRejectionRepository extends JpaRepository<NxOrderRejection, UUID> {
    List<NxOrderRejection> findByTenantId(UUID tenantId);
    List<NxOrderRejection> findByOrderId(UUID orderId);
    List<NxOrderRejection> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxOrderRejection> findByTenantIdAndRejectionCode(UUID tenantId, String rejectionCode);
    List<NxOrderRejection> findByOrderItemId(UUID orderItemId);
}
