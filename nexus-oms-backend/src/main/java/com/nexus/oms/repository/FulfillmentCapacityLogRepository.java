package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFulfillmentCapacityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FulfillmentCapacityLogRepository extends JpaRepository<NxFulfillmentCapacityLog, UUID> {
    List<NxFulfillmentCapacityLog> findByNodeId(UUID nodeId);
    List<NxFulfillmentCapacityLog> findByNodeIdAndCreatedAtBetween(UUID nodeId, LocalDateTime start, LocalDateTime end);
    List<NxFulfillmentCapacityLog> findByTenantId(UUID tenantId);
}
