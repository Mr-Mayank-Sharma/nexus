package com.nexus.oms.repository;

import com.nexus.oms.entity.NxEmailParsedOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmailParsedOrderRepository extends JpaRepository<NxEmailParsedOrder, UUID> {
    Page<NxEmailParsedOrder> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxEmailParsedOrder> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    Optional<NxEmailParsedOrder> findByEmailMessageId(String emailMessageId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}
