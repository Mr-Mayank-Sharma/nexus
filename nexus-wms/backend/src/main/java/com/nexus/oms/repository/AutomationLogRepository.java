package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAutomationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AutomationLogRepository extends JpaRepository<NxAutomationLog, UUID> {

    List<NxAutomationLog> findByTenantId(UUID tenantId);

    List<NxAutomationLog> findBySystemId(UUID systemId);

    List<NxAutomationLog> findBySystemIdAndCreatedAtBetween(UUID systemId, LocalDateTime from, LocalDateTime to);

    List<NxAutomationLog> findBySystemIdAndLogLevel(UUID systemId, String logLevel);

    List<NxAutomationLog> findTop100ByOrderByCreatedAtDesc();
}
