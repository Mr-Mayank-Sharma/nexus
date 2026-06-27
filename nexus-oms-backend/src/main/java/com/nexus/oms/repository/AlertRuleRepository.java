package com.nexus.oms.repository;

import com.nexus.oms.entity.AlertRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    Page<AlertRule> findByTenantId(UUID tenantId, Pageable pageable);

    List<AlertRule> findByTenantIdAndEventType(UUID tenantId, String eventType);

    List<AlertRule> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    List<AlertRule> findByTenantIdAndSeverity(UUID tenantId, String severity);
}
