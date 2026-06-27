package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRoutingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RoutingLogRepository extends JpaRepository<NxRoutingLog, UUID> {
    List<NxRoutingLog> findByOrderId(UUID orderId);
    Page<NxRoutingLog> findByTenantId(UUID tenantId, Pageable pageable);
}
