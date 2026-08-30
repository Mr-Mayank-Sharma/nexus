package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncJobStatusRepository extends JpaRepository<NxSyncJobStatus, UUID> {

    List<NxSyncJobStatus> findByTenantId(UUID tenantId);

    Optional<NxSyncJobStatus> findByTenantIdAndJobName(UUID tenantId, String jobName);

    List<NxSyncJobStatus> findByTenantIdAndStatus(UUID tenantId, String status);
}
