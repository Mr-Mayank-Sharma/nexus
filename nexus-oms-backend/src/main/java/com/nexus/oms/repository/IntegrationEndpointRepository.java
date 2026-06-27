package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationEndpointRepository extends JpaRepository<IntegrationEndpoint, UUID> {

    Page<IntegrationEndpoint> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<IntegrationEndpoint> findByTenantIdAndName(UUID tenantId, String name);

    Page<IntegrationEndpoint> findByTenantIdAndEndpointType(UUID tenantId, String endpointType, Pageable pageable);

    Page<IntegrationEndpoint> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive, Pageable pageable);
}
