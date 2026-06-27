package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NxSyncLogRepository extends JpaRepository<NxSyncLog, UUID> {
    Page<NxSyncLog> findByTenantIdAndIntegrationTypeOrderByCreatedAtDesc(UUID tenantId, String integrationType, Pageable pageable);
}
