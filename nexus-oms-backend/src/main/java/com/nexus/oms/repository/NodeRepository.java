package com.nexus.oms.repository;

import com.nexus.oms.entity.NxNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NodeRepository extends JpaRepository<NxNode, UUID> {

    List<NxNode> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<NxNode> findByTenantId(UUID tenantId);
}
