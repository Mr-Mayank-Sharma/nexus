package com.nexus.oms.repository;

import com.nexus.oms.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByTenantId(UUID tenantId, Pageable pageable);

    List<NotificationLog> findByTenantIdAndStatus(UUID tenantId, String status);

    List<NotificationLog> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
