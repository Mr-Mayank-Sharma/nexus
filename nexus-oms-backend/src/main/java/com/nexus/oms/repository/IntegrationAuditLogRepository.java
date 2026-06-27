package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface IntegrationAuditLogRepository extends JpaRepository<IntegrationAuditLog, UUID> {

    Page<IntegrationAuditLog> findByTenantId(UUID tenantId, Pageable pageable);

    List<IntegrationAuditLog> findByFlowId(UUID flowId);

    Page<IntegrationAuditLog> findByTenantIdAndAction(UUID tenantId, String action, Pageable pageable);

    Page<IntegrationAuditLog> findByTenantIdAndEntityType(UUID tenantId, String entityType, Pageable pageable);

    Page<IntegrationAuditLog> findByTenantIdAndCreatedAtBetween(UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
