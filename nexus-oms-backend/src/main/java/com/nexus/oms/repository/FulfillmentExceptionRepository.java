package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFulfillmentException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FulfillmentExceptionRepository extends JpaRepository<NxFulfillmentException, UUID> {
    List<NxFulfillmentException> findByOrderId(UUID orderId);
    Page<NxFulfillmentException> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxFulfillmentException> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    Page<NxFulfillmentException> findByTenantIdAndSeverity(UUID tenantId, String severity, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByTenantIdAndSeverity(UUID tenantId, String severity);
}
