package com.nexus.oms.repository;

import com.nexus.oms.entity.IntegrationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationMessageRepository extends JpaRepository<IntegrationMessage, UUID> {

    Page<IntegrationMessage> findByTenantId(UUID tenantId, Pageable pageable);

    List<IntegrationMessage> findByFlowId(UUID flowId);

    Page<IntegrationMessage> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Optional<IntegrationMessage> findByMessageIdAndTenantId(String messageId, UUID tenantId);

    List<IntegrationMessage> findByCorrelationId(String correlationId);
}
