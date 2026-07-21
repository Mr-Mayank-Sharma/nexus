package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPicker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PickerRepository extends JpaRepository<NxPicker, UUID> {
    List<NxPicker> findByTenantId(UUID tenantId);
    List<NxPicker> findByNodeId(UUID nodeId);
    List<NxPicker> findByNodeIdAndStatus(UUID nodeId, String status);
    List<NxPicker> findByNodeIdAndActiveTrue(UUID nodeId);
    NxPicker findByUserId(UUID userId);

    @Query("SELECT p FROM NxPicker p WHERE p.nodeId = :nodeId AND p.status = 'AVAILABLE' AND p.active = true ORDER BY p.ordersCompletedToday ASC")
    List<NxPicker> findAvailablePickers(@Param("nodeId") UUID nodeId);

    @Query("SELECT p FROM NxPicker p WHERE p.nodeId = :nodeId AND p.active = true AND p.status != 'OFFLINE' ORDER BY p.itemsPickedToday DESC")
    List<NxPicker> findActivePickers(@Param("nodeId") UUID nodeId);
}
