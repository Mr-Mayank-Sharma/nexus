package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAsn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NxAsnRepository extends JpaRepository<NxAsn, UUID> {
    Page<NxAsn> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxAsn> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    Optional<NxAsn> findByTenantIdAndAsnNumber(UUID tenantId, String asnNumber);
    boolean existsByTenantIdAndAsnNumber(UUID tenantId, String asnNumber);
}
