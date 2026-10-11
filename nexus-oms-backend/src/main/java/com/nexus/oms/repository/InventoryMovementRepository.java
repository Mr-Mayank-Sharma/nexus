package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventoryMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<NxInventoryMovement, UUID> {

    List<NxInventoryMovement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<NxInventoryMovement> findByTenantIdAndSkuOrderByCreatedAtDesc(UUID tenantId, String sku, Pageable pageable);

    List<NxInventoryMovement> findByTenantIdAndSkuAndNodeIdOrderByCreatedAtDesc(UUID tenantId, String sku, UUID nodeId, Pageable pageable);

    /** (inventoryId, sum of on-hand deltas) for every row that has ledger lines. */
    @Query("SELECT m.inventoryId, COALESCE(SUM(m.onHandDelta), 0) FROM NxInventoryMovement m WHERE m.tenantId = :tenantId GROUP BY m.inventoryId")
    List<Object[]> sumOnHandByInventory(@Param("tenantId") UUID tenantId);

    /** Sum of on-hand deltas for a SKU; compared with the stock rows to prove the ledger explains them. */
    @Query("SELECT COALESCE(SUM(m.onHandDelta), 0) FROM NxInventoryMovement m WHERE m.tenantId = :tenantId AND m.sku = :sku")
    Long sumOnHandDelta(@Param("tenantId") UUID tenantId, @Param("sku") String sku);
}
