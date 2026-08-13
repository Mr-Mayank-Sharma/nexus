package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventoryReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface NxInventoryReceiptRepository extends JpaRepository<NxInventoryReceipt, UUID> {
    Page<NxInventoryReceipt> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxInventoryReceipt> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query(value = "SELECT * FROM nx_inventory_receipts WHERE tenant_id = :tenantId " +
            "AND (CAST(id AS varchar) LIKE CONCAT('%', :q, '%') " +
            "OR LOWER(COALESCE(sku,'')) LIKE CONCAT('%', LOWER(:q), '%') " +
            "OR LOWER(COALESCE(product_name,'')) LIKE CONCAT('%', LOWER(:q), '%') " +
            "OR LOWER(COALESCE(reference_number,'')) LIKE CONCAT('%', LOWER(:q), '%')) " +
            "ORDER BY created_at DESC LIMIT 20", nativeQuery = true)
    List<NxInventoryReceipt> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
