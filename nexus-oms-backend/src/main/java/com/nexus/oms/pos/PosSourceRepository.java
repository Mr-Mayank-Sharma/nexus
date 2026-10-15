package com.nexus.oms.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosSourceRepository extends JpaRepository<NxPosSource, UUID> {
    List<NxPosSource> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<NxPosSource> findByNodeId(UUID nodeId);
}
