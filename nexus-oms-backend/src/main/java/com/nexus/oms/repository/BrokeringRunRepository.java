package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBrokeringRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BrokeringRunRepository extends JpaRepository<NxBrokeringRun, UUID> {
    List<NxBrokeringRun> findByTenantId(UUID tenantId);
    List<NxBrokeringRun> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
    List<NxBrokeringRun> findByTenantIdAndStatus(UUID tenantId, String status);
}
