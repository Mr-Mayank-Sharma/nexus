package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRoutingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoutingConfigRepository extends JpaRepository<NxRoutingConfig, UUID> {
    Optional<NxRoutingConfig> findByTenantId(UUID tenantId);
}
