package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRfidScanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfidScanSessionRepository extends JpaRepository<NxRfidScanSession, UUID> {

    List<NxRfidScanSession> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<NxRfidScanSession> findByTenantIdAndId(UUID tenantId, UUID id);
}
