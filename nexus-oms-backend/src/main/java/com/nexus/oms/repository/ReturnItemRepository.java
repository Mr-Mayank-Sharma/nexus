package com.nexus.oms.repository;

import com.nexus.oms.entity.NxReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReturnItemRepository extends JpaRepository<NxReturnItem, UUID> {
    List<NxReturnItem> findByReturnId(UUID returnId);
    List<NxReturnItem> findByReturnIdAndStatus(UUID returnId, String status);
    List<NxReturnItem> findByTenantId(UUID tenantId);
}
