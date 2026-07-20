package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSlottingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SlottingRuleRepository extends JpaRepository<NxSlottingRule, UUID> {

    List<NxSlottingRule> findByTenantId(UUID tenantId);

    List<NxSlottingRule> findByWarehouseIdAndIsActive(UUID warehouseId, Boolean isActive);

    List<NxSlottingRule> findByWarehouseIdAndRuleType(UUID warehouseId, String ruleType);

    List<NxSlottingRule> findByWarehouseIdAndIsActiveOrderByPriorityAsc(UUID warehouseId, Boolean isActive);
}
