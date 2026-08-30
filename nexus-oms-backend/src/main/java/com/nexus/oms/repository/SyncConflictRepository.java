package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSyncConflict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncConflictRepository extends JpaRepository<NxSyncConflict, UUID> {

    Page<NxSyncConflict> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<NxSyncConflict> findByTenantId(UUID tenantId, Pageable pageable);

    List<NxSyncConflict> findByTenantIdAndStatus(UUID tenantId, String status);

    List<NxSyncConflict> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    Optional<NxSyncConflict> findByTenantIdAndEntityTypeAndEntityIdAndStatus(
            UUID tenantId, String entityType, UUID entityId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
