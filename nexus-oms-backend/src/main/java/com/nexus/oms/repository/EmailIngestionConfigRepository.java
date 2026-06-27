package com.nexus.oms.repository;

import com.nexus.oms.entity.NxEmailIngestionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmailIngestionConfigRepository extends JpaRepository<NxEmailIngestionConfig, UUID> {
    Optional<NxEmailIngestionConfig> findByTenantId(UUID tenantId);
}
