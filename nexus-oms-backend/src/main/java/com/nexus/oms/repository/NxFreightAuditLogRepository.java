package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFreightAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxFreightAuditLogRepository extends JpaRepository<NxFreightAuditLog, UUID> {
    List<NxFreightAuditLog> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
}
