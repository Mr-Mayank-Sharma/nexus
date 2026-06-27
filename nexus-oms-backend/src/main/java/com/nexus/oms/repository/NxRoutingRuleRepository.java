package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRoutingRule;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NxRoutingRuleRepository extends JpaRepository<NxRoutingRule, UUID> {
    List<NxRoutingRule> findByTenantIdAndIsActiveTrue(UUID tenantId);
    List<NxRoutingRule> findByTenantId(UUID tenantId, Sort sort);
}
