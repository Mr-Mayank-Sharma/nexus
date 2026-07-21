package com.nexus.oms.repository;

import com.nexus.oms.entity.NxParkedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ParkedOrderRepository extends JpaRepository<NxParkedOrder, UUID> {
    List<NxParkedOrder> findByTenantId(UUID tenantId);
    List<NxParkedOrder> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxParkedOrder> findByTenantIdAndReason(UUID tenantId, String reason);
    List<NxParkedOrder> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
    NxParkedOrder findByOrderId(UUID orderId);
    List<NxParkedOrder> findByTenantIdAndSku(UUID tenantId, String sku);
    List<NxParkedOrder> findByStatusAndExpectedDateBefore(String status, java.time.LocalDateTime expectedDate);
}
