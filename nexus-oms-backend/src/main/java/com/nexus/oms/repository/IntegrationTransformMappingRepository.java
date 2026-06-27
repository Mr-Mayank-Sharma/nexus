package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationTransformMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationTransformMappingRepository extends JpaRepository<IntegrationTransformMapping, UUID> {

    Page<IntegrationTransformMapping> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<IntegrationTransformMapping> findByTenantIdAndName(UUID tenantId, String name);

    List<IntegrationTransformMapping> findByTenantIdAndSourceFormatAndTargetFormat(UUID tenantId, String sourceFormat, String targetFormat);

    Page<IntegrationTransformMapping> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive, Pageable pageable);
}
