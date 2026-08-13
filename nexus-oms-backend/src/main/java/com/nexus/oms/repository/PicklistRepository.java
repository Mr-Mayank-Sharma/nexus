package com.nexus.oms.repository;

import com.nexus.oms.entity.NxPicklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PicklistRepository extends JpaRepository<NxPicklist, UUID> {
    List<NxPicklist> findByTenantId(UUID tenantId);
    List<NxPicklist> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxPicklist> findByAssigneeId(UUID assigneeId);
    long countByTenantIdAndStatus(UUID tenantId, String status);

    @Query(value = "SELECT * FROM nx_picklists WHERE tenant_id = :tenantId " +
            "AND (LOWER(COALESCE(name,'')) LIKE CONCAT('%', LOWER(:q), '%') OR CAST(id AS varchar) LIKE CONCAT('%', :q, '%')) " +
            "ORDER BY created_at DESC LIMIT 20", nativeQuery = true)
    List<NxPicklist> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
