package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<NxInventory, UUID> {

    List<NxInventory> findByTenantId(UUID tenantId);

    Optional<NxInventory> findByTenantIdAndSkuAndNodeId(UUID tenantId, String sku, UUID nodeId);

    List<NxInventory> findByTenantIdAndNodeId(UUID tenantId, UUID nodeId);

    List<NxInventory> findByTenantIdAndSku(UUID tenantId, String sku);

    @Modifying
    @Query("UPDATE NxInventory i SET i.quantityAllocated = COALESCE(i.quantityAllocated, 0) + :qty, " +
           "i.version = i.version + 1 " +
           "WHERE i.tenantId = :tenantId AND i.sku = :sku AND (i.nodeId = :nodeId OR i.nodeId IS NULL) " +
           "AND (COALESCE(i.quantityOnHand, 0) - COALESCE(i.quantityAllocated, 0) - COALESCE(i.quantityReserved, 0)) >= :qty")
    int reserveAtomic(@Param("tenantId") UUID tenantId, @Param("sku") String sku,
                      @Param("nodeId") UUID nodeId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE NxInventory i SET i.quantityAllocated = CASE WHEN COALESCE(i.quantityAllocated, 0) >= :qty THEN COALESCE(i.quantityAllocated, 0) - :qty ELSE 0 END, " +
           "i.version = i.version + 1 " +
           "WHERE i.tenantId = :tenantId AND i.sku = :sku AND (i.nodeId = :nodeId OR i.nodeId IS NULL)")
    int releaseAtomic(@Param("tenantId") UUID tenantId, @Param("sku") String sku,
                      @Param("nodeId") UUID nodeId, @Param("qty") int qty);

    @Query("SELECT COALESCE(SUM(i.quantityOnHand), 0) FROM NxInventory i WHERE i.tenantId = :tenantId AND i.sku = :sku")
    Integer getTotalOnHand(@Param("tenantId") UUID tenantId, @Param("sku") String sku);

    @Query("SELECT COALESCE(SUM(COALESCE(i.quantityOnHand, 0) - COALESCE(i.quantityAllocated, 0) - COALESCE(i.quantityReserved, 0)), 0) FROM NxInventory i WHERE i.tenantId = :tenantId AND i.sku = :sku")
    Integer getAvailableToPromise(@Param("tenantId") UUID tenantId, @Param("sku") String sku);
}
