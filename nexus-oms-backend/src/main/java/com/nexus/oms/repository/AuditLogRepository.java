package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<NxAuditLog, UUID> {
}
