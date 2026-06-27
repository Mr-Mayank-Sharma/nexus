package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPicklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PicklistItemRepository extends JpaRepository<NxPicklistItem, UUID> {
    List<NxPicklistItem> findByPicklistId(UUID picklistId);
    List<NxPicklistItem> findByOrderId(UUID orderId);
    List<NxPicklistItem> findByPicklistIdAndStatus(UUID picklistId, String status);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByPicklistIdAndStatus(UUID picklistId, String status);
}
