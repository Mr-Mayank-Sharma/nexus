package com.nexus.oms.repository;

import com.nexus.oms.entity.Rfq;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RfqRepository extends JpaRepository<Rfq, UUID> {

    Page<Rfq> findByTenantId(UUID tenantId, Pageable pageable);

    List<Rfq> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Rfq> findByRequestId(UUID requestId);
}
