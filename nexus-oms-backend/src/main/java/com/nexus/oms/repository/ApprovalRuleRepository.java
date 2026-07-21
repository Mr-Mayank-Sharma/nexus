package com.nexus.oms.repository;

import com.nexus.oms.entity.NxApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRuleRepository extends JpaRepository<NxApprovalRule, UUID> {
    List<NxApprovalRule> findByTenantId(UUID tenantId);
    List<NxApprovalRule> findByTenantIdAndActiveTrue(UUID tenantId);
    List<NxApprovalRule> findByTenantIdAndRuleType(UUID tenantId, String ruleType);
    List<NxApprovalRule> findByTenantIdOrderByPriorityAsc(UUID tenantId);
}
