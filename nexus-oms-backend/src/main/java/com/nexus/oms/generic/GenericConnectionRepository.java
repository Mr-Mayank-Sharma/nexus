package com.nexus.oms.generic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenericConnectionRepository extends JpaRepository<NxGenericConnection, UUID> {
    List<NxGenericConnection> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<NxGenericConnection> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
