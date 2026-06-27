package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationValidationRuleRepository extends JpaRepository<IntegrationValidationRule, UUID> {

    List<IntegrationValidationRule> findByTenantId(UUID tenantId);

    List<IntegrationValidationRule> findByTenantIdAndEntityType(UUID tenantId, String entityType);

    List<IntegrationValidationRule> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
}
