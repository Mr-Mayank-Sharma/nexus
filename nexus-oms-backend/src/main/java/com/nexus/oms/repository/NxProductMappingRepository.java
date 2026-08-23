package com.nexus.oms.repository;

import com.nexus.oms.entity.NxProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxProductMappingRepository extends JpaRepository<NxProductMapping, UUID> {
    Optional<NxProductMapping> findByTenantIdAndBcSku(UUID tenantId, String bcSku);
    Optional<NxProductMapping> findByTenantIdAndBcProductId(UUID tenantId, Long bcProductId);
    List<NxProductMapping> findAllByTenantIdAndBcSku(UUID tenantId, String bcSku);
    List<NxProductMapping> findAllByTenantIdAndBcProductId(UUID tenantId, Long bcProductId);
    List<NxProductMapping> findByTenantId(UUID tenantId);
}
