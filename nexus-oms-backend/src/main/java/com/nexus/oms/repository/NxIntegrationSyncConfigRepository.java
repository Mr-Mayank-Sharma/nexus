package com.nexus.oms.repository;

import com.nexus.oms.entity.NxIntegrationSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxIntegrationSyncConfigRepository extends JpaRepository<NxIntegrationSyncConfig, UUID> {
    List<NxIntegrationSyncConfig> findByStoreId(UUID storeId);
    Optional<NxIntegrationSyncConfig> findByStoreIdAndSyncType(UUID storeId, String syncType);

    List<NxIntegrationSyncConfig> findByEnabledTrue();
}
