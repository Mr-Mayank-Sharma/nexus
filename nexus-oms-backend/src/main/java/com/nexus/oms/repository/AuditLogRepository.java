package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<NxAuditLog, UUID> {
    List<NxAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
}
