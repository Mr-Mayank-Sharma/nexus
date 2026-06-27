package com.nexus.oms.repository;

import com.nexus.oms.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

    Optional<CompanySettings> findByTenantId(UUID tenantId);
}
