package com.nexus.oms.repository;

import com.nexus.oms.entity.NxATPSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ATPSnapshotRepository extends JpaRepository<NxATPSnapshot, UUID> {
    List<NxATPSnapshot> findByTenantId(UUID tenantId);
    List<NxATPSnapshot> findByNodeIdAndSku(UUID nodeId, String sku);
    NxATPSnapshot findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(UUID nodeId, String sku);

    @Query("SELECT s FROM NxATPSnapshot s WHERE s.tenantId = :tenantId AND s.atpQuantity > 0")
    List<NxATPSnapshot> findAvailableStock(@Param("tenantId") UUID tenantId);

    @Query("SELECT s FROM NxATPSnapshot s WHERE s.nodeId = :nodeId AND s.sku = :sku AND s.atpQuantity >= :quantity")
    List<NxATPSnapshot> findNodesWithAvailableStock(
        @Param("tenantId") UUID tenantId,
        @Param("sku") String sku,
        @Param("quantity") Integer quantity
    );

    @Query("SELECT s FROM NxATPSnapshot s WHERE s.tenantId = :tenantId AND s.sku = :sku ORDER BY s.snapshotDate DESC")
    List<NxATPSnapshot> findBySku(@Param("tenantId") UUID tenantId, @Param("sku") String sku);
}
