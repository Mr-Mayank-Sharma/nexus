package com.nexus.oms.repository;

import com.nexus.oms.entity.NxEngineeredStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EngineeredStandardRepository extends JpaRepository<NxEngineeredStandard, UUID> {

    List<NxEngineeredStandard> findByTenantId(UUID tenantId);

    List<NxEngineeredStandard> findByTenantIdAndTaskType(UUID tenantId, String taskType);

    List<NxEngineeredStandard> findByWarehouseIdAndTaskType(UUID warehouseId, String taskType);

    List<NxEngineeredStandard> findByWarehouseIdAndIsActive(UUID warehouseId, Boolean isActive);

    List<NxEngineeredStandard> findByWarehouseIdAndCategory(UUID warehouseId, String category);
}
