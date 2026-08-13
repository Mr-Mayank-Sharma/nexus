package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PackageRepository extends JpaRepository<NxPackage, UUID> {
    List<NxPackage> findByTenantId(UUID tenantId);
    List<NxPackage> findByOrderId(UUID orderId);
    List<NxPackage> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxPackage> findByPicklistId(UUID picklistId);
    long countByTenantIdAndStatus(UUID tenantId, String status);

    @Query(value = "SELECT * FROM nx_packages WHERE tenant_id = :tenantId " +
            "AND (CAST(id AS varchar) LIKE CONCAT('%', :q, '%') OR CAST(order_id AS varchar) LIKE CONCAT('%', :q, '%') " +
            "OR LOWER(COALESCE(tracking_number,'')) LIKE CONCAT('%', LOWER(:q), '%') OR LOWER(COALESCE(box_name,'')) LIKE CONCAT('%', LOWER(:q), '%')) " +
            "ORDER BY created_at DESC LIMIT 20", nativeQuery = true)
    List<NxPackage> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
