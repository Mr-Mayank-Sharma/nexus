package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFulfillmentLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FulfillmentLimitRepository extends JpaRepository<NxFulfillmentLimit, UUID> {
    List<NxFulfillmentLimit> findByTenantId(UUID tenantId);
    NxFulfillmentLimit findByNodeId(UUID nodeId);
    NxFulfillmentLimit findByTenantIdAndNodeId(UUID tenantId, UUID nodeId);
}
