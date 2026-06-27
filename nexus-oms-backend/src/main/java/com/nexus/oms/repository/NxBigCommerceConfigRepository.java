package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBigCommerceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NxBigCommerceConfigRepository extends JpaRepository<NxBigCommerceConfig, UUID> {
    Optional<NxBigCommerceConfig> findByTenantId(UUID tenantId);
    Optional<NxBigCommerceConfig> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
