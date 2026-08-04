package com.nexus.oms.repository;

import com.nexus.oms.entity.NxManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ManifestRepository extends JpaRepository<NxManifest, UUID> {

    List<NxManifest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
