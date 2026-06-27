package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationCDCEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IntegrationCDCEventRepository extends JpaRepository<IntegrationCDCEvent, UUID> {

    List<IntegrationCDCEvent> findByTenantIdAndSourceAndEntityTypeAndProcessed(UUID tenantId, String source, String entityType, Boolean processed);

    List<IntegrationCDCEvent> findByTenantIdAndProcessed(UUID tenantId, Boolean processed);

    long countByTenantIdAndProcessed(UUID tenantId, Boolean processed);
}
