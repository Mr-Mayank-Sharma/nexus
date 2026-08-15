package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBillingStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingStatementRepository extends JpaRepository<NxBillingStatement, UUID> {

    List<NxBillingStatement> findByTenantId(UUID tenantId);

    List<NxBillingStatement> findByTenantIdAndClientId(UUID tenantId, UUID clientId);

    List<NxBillingStatement> findByTenantIdAndStatus(UUID tenantId, String status);

    List<NxBillingStatement> findByTenantIdAndClientIdAndStatus(UUID tenantId, UUID clientId, String status);

    Optional<NxBillingStatement> findFirstByTenantIdAndClientIdAndPeriodStartAndPeriodEnd(
            UUID tenantId, UUID clientId, LocalDate periodStart, LocalDate periodEnd);
}
