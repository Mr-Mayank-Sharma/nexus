package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPickupOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PickupOrderRepository extends JpaRepository<NxPickupOrder, UUID> {
    List<NxPickupOrder> findByTenantId(UUID tenantId);
    List<NxPickupOrder> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxPickupOrder> findByNodeIdAndStatus(UUID nodeId, String status);
    List<NxPickupOrder> findByPickerId(UUID pickerId);
    List<NxPickupOrder> findByNodeIdAndPickerId(UUID nodeId, UUID pickerId);
    NxPickupOrder findByOrderId(UUID orderId);
    NxPickupOrder findByPickupCode(String pickupCode);

    @Query("SELECT p FROM NxPickupOrder p WHERE p.nodeId = :nodeId AND p.status IN ('PENDING', 'PICKING') ORDER BY p.createdAt ASC")
    List<NxPickupOrder> findPendingPickupsByNode(@Param("nodeId") UUID nodeId);

    @Query("SELECT p FROM NxPickupOrder p WHERE p.tenantId = :tenantId AND p.status = 'READY_FOR_HANDOFF' ORDER BY p.readyAt ASC")
    List<NxPickupOrder> findReadyForHandoff(@Param("tenantId") UUID tenantId);
}
