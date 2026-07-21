package com.nexus.oms.repository;

import com.nexus.oms.entity.NxEndlessAisleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EndlessAisleOrderRepository extends JpaRepository<NxEndlessAisleOrder, UUID> {

    List<NxEndlessAisleOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<NxEndlessAisleOrder> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<NxEndlessAisleOrder> findByStoreIdOrderByCreatedAtDesc(UUID storeId);

    List<NxEndlessAisleOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("SELECT e FROM NxEndlessAisleOrder e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<NxEndlessAisleOrder> findAllByTenant(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
