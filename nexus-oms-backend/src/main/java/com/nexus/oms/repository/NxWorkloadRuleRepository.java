package com.nexus.oms.repository;

import com.nexus.oms.entity.NxWorkloadRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxWorkloadRuleRepository extends JpaRepository<NxWorkloadRule, UUID> {
    List<NxWorkloadRule> findByTenantIdAndWarehouseIdAndIsActiveTrue(UUID tenantId, UUID warehouseId);
    List<NxWorkloadRule> findByTenantIdAndWarehouseIdAndTaskTypeAndIsActiveTrue(UUID tenantId, UUID warehouseId, String taskType);
}
