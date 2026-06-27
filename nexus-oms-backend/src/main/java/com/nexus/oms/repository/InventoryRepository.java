package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventory;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("SELECT COALESCE(SUM(i.quantityOnHand), 0) FROM NxInventory i WHERE i.tenantId = :tenantId AND i.sku = :sku")
    Integer getTotalOnHand(@Param("tenantId") UUID tenantId, @Param("sku") String sku);

    @Query("SELECT COALESCE(SUM(i.quantityOnHand - i.quantityAllocated - i.quantityReserved), 0) FROM NxInventory i WHERE i.tenantId = :tenantId AND i.sku = :sku")
    Integer getAvailableToPromise(@Param("tenantId") UUID tenantId, @Param("sku") String sku);
}
