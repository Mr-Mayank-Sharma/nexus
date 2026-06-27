package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventoryReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NxInventoryReceiptRepository extends JpaRepository<NxInventoryReceipt, UUID> {
    Page<NxInventoryReceipt> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxInventoryReceipt> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
