package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrderApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderApprovalRepository extends JpaRepository<NxOrderApproval, UUID> {
    List<NxOrderApproval> findByTenantId(UUID tenantId);
    List<NxOrderApproval> findByTenantIdAndStatus(UUID tenantId, String status);
    NxOrderApproval findByOrderId(UUID orderId);
    List<NxOrderApproval> findByCustomerId(UUID customerId);
}
