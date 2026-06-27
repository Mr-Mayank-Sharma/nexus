package com.nexus.oms.repository;

import com.nexus.oms.entity.NxIntegrationStore;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxIntegrationStoreRepository extends JpaRepository<NxIntegrationStore, UUID> {
    List<NxIntegrationStore> findByTenantId(UUID tenantId);
    List<NxIntegrationStore> findByTenantIdAndPlatform(UUID tenantId, String platform);
    Optional<NxIntegrationStore> findByTenantIdAndStoreCode(UUID tenantId, String storeCode);
}
