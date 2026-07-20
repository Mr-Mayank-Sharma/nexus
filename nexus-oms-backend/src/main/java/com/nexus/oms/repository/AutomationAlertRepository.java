package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAutomationAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AutomationAlertRepository extends JpaRepository<NxAutomationAlert, UUID> {

    List<NxAutomationAlert> findByTenantId(UUID tenantId);

    List<NxAutomationAlert> findBySystemId(UUID systemId);

    @Query("SELECT a FROM NxAutomationAlert a JOIN NxAutomationSystem s ON a.systemId = s.id WHERE s.warehouseId = :warehouseId AND (:status IS NULL OR a.status = :status)")
    List<NxAutomationAlert> findByWarehouseIdAndStatus(@Param("warehouseId") UUID warehouseId, @Param("status") String status);

    List<NxAutomationAlert> findBySystemIdAndStatus(UUID systemId, String status);

    @Query("SELECT a FROM NxAutomationAlert a JOIN NxAutomationSystem s ON a.systemId = s.id WHERE s.warehouseId = :warehouseId AND (:severity IS NULL OR a.severity = :severity)")
    List<NxAutomationAlert> findByWarehouseIdAndSeverity(@Param("warehouseId") UUID warehouseId, @Param("severity") String severity);
}
