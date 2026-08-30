package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSyncFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncFieldMappingRepository extends JpaRepository<NxSyncFieldMapping, UUID> {

    List<NxSyncFieldMapping> findByTenantIdAndEntityTypeAndDirectionAndIsActiveTrue(
            UUID tenantId, String entityType, String direction);

    Optional<NxSyncFieldMapping> findByTenantIdAndEntityTypeAndFieldNameAndDirection(
            UUID tenantId, String entityType, String fieldName, String direction);

    List<NxSyncFieldMapping> findByTenantId(UUID tenantId);
}
