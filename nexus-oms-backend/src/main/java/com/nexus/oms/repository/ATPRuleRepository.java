package com.nexus.oms.repository;

import com.nexus.oms.entity.NxATPRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ATPRuleRepository extends JpaRepository<NxATPRule, UUID> {
    List<NxATPRule> findByTenantId(UUID tenantId);
    List<NxATPRule> findByTenantIdAndActiveTrue(UUID tenantId);
    List<NxATPRule> findByTenantIdAndRuleType(UUID tenantId, String ruleType);
    List<NxATPRule> findByTenantIdOrderByPriorityAsc(UUID tenantId);
}
