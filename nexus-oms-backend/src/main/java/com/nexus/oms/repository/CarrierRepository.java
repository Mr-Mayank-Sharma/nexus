package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCarrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarrierRepository extends JpaRepository<NxCarrier, UUID> {
    Page<NxCarrier> findByTenantId(UUID tenantId, Pageable pageable);
    List<NxCarrier> findByTenantIdAndIsActiveTrue(UUID tenantId);
    Optional<NxCarrier> findByTenantIdAndCode(UUID tenantId, String code);
    List<NxCarrier> findByTenantIdAndStatus(UUID tenantId, String status);
}
