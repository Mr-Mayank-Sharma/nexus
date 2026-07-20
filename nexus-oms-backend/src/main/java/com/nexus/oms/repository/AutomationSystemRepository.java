package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAutomationSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AutomationSystemRepository extends JpaRepository<NxAutomationSystem, UUID> {

    List<NxAutomationSystem> findByTenantId(UUID tenantId);

    List<NxAutomationSystem> findByWarehouseId(UUID warehouseId);

    List<NxAutomationSystem> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxAutomationSystem> findByWarehouseIdAndSystemType(UUID warehouseId, String systemType);

    List<NxAutomationSystem> findByWarehouseIdAndIsActive(UUID warehouseId, Boolean isActive);
}
