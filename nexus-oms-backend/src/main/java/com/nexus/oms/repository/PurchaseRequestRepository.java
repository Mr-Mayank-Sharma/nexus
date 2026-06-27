package com.nexus.oms.repository;

import com.nexus.oms.entity.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {

    Page<PurchaseRequest> findByTenantId(UUID tenantId, Pageable pageable);

    List<PurchaseRequest> findByTenantIdAndStatus(UUID tenantId, String status);

    List<PurchaseRequest> findByTenantIdAndRequestedBy(UUID tenantId, String requestedBy);

    List<PurchaseRequest> findByTenantIdAndPriority(UUID tenantId, String priority);
}
