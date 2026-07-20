package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAutomationCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AutomationCommandRepository extends JpaRepository<NxAutomationCommand, UUID> {

    List<NxAutomationCommand> findByTenantId(UUID tenantId);

    List<NxAutomationCommand> findBySystemId(UUID systemId);

    List<NxAutomationCommand> findBySystemIdAndStatus(UUID systemId, String status);

    @Query("SELECT c FROM NxAutomationCommand c JOIN NxAutomationSystem s ON c.systemId = s.id WHERE s.warehouseId = :warehouseId AND (:status IS NULL OR c.status = :status)")
    List<NxAutomationCommand> findByWarehouseIdAndStatus(@Param("warehouseId") UUID warehouseId, @Param("status") String status);

    List<NxAutomationCommand> findByWaveId(UUID waveId);

    List<NxAutomationCommand> findByPicklistId(UUID picklistId);

    List<NxAutomationCommand> findTop100ByOrderByCreatedAtDesc();
}
