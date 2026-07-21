package com.nexus.oms.repository;

import com.nexus.oms.entity.NxReplenishmentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxReplenishmentRuleRepository extends JpaRepository<NxReplenishmentRule, UUID> {
    List<NxReplenishmentRule> findByWarehouseIdAndIsActiveTrue(UUID warehouseId);
    List<NxReplenishmentRule> findByWarehouseIdAndRuleTypeAndIsActiveTrue(UUID warehouseId, String ruleType);
    List<NxReplenishmentRule> findByWarehouseIdAndItemCategoryAndIsActiveTrue(UUID warehouseId, String itemCategory);
}
