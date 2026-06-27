package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PackageRepository extends JpaRepository<NxPackage, UUID> {
    List<NxPackage> findByTenantId(UUID tenantId);
    List<NxPackage> findByOrderId(UUID orderId);
    List<NxPackage> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxPackage> findByPicklistId(UUID picklistId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}
