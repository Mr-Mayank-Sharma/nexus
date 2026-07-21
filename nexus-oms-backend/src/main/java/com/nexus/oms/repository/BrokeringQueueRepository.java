package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBrokeringQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BrokeringQueueRepository extends JpaRepository<NxBrokeringQueue, UUID> {
    List<NxBrokeringQueue> findByTenantId(UUID tenantId);
    List<NxBrokeringQueue> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxBrokeringQueue> findByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);
    List<NxBrokeringQueue> findByOrderId(UUID orderId);
    List<NxBrokeringQueue> findByStatusAndNextRunAtBefore(String status, java.time.LocalDateTime nextRunAt);
    List<NxBrokeringQueue> findByStatusAndPriorityIn(String status, List<String> priorities);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}
