package com.nexus.oms.repository;

import com.nexus.oms.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Page<PurchaseOrder> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<PurchaseOrder> findByTenantIdAndPoNumber(UUID tenantId, String poNumber);

    List<PurchaseOrder> findByTenantIdAndStatus(UUID tenantId, String status);

    List<PurchaseOrder> findBySupplierId(UUID supplierId);

    List<PurchaseOrder> findByTenantIdAndIsFullyReceived(UUID tenantId, boolean isFullyReceived);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
