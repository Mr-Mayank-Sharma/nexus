package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCycleCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface NxCycleCountRepository extends JpaRepository<NxCycleCount, UUID> {
    Page<NxCycleCount> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxCycleCount> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query(value = "SELECT * FROM nx_cycle_counts WHERE tenant_id = :tenantId " +
            "AND (CAST(id AS varchar) LIKE CONCAT('%', :q, '%') " +
            "OR LOWER(COALESCE(sku,'')) LIKE CONCAT('%', LOWER(:q), '%') " +
            "OR LOWER(COALESCE(product_name,'')) LIKE CONCAT('%', LOWER(:q), '%')) " +
            "ORDER BY created_at DESC LIMIT 20", nativeQuery = true)
    List<NxCycleCount> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
