package com.nexus.oms.repository;

import com.nexus.oms.entity.NxProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NxProductMappingRepository extends JpaRepository<NxProductMapping, UUID> {
    Optional<NxProductMapping> findByTenantIdAndBcSku(UUID tenantId, String bcSku);
    Optional<NxProductMapping> findByTenantIdAndBcProductId(UUID tenantId, Integer bcProductId);
}
