package com.nexus.oms.repository;

import com.nexus.oms.entity.NxKitTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitTemplateRepository extends JpaRepository<NxKitTemplate, UUID> {

    List<NxKitTemplate> findByTenantId(UUID tenantId);

    Optional<NxKitTemplate> findByTenantIdAndKitSku(UUID tenantId, String kitSku);
}
