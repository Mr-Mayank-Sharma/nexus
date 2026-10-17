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

    /** Tenant-level (location-less) stock row; a derived query with a null param would never match. */
    Optional<NxInventory> findByTenantIdAndSkuAndNodeIdIsNull(UUID tenantId, String sku);

    List<NxInventory> findByTenantIdAndNodeId(UUID tenantId, UUID nodeId);

    List<NxInventory> findByTenantIdAndSku(UUID tenantId, String sku);

    /**
     * Reserves against ONE resolved inventory row.
     *
     * <p>The previous form of this query matched {@code (nodeId = :nodeId OR nodeId IS NULL)} in a
     * single bulk UPDATE. When a SKU had both a node-specific row and a shared pool row, that
     * statement decremented <em>both</em> rows by the full requested quantity — committing 3 units
     * when 3 were requested against 2 on-hand at the node. Callers now resolve the single backing
     * row first (see {@code InventoryService#resolveStock}) and reserve against it by id, so the
     * guarded predicate can only ever apply to one row.
     *
     * <p>The guard also withholds {@code safetyStock}. Because this is a single guarded UPDATE it —
     * not any Java-side precondition — is what actually stops a reservation from consuming stock the
     * node is supposed to hold back. It therefore has to stay arithmetically identical to
     * {@code InventoryService#available}, which is what the allocator reads to decide node
     * eligibility; the two drifting apart is how a node gets promised units the database then
     * refuses. No {@code MAX(..., 0)} is needed here: a negative left-hand side simply fails
     * {@code >= :qty} for any positive quantity, which is the outcome we want.
     */
    @Modifying
    @Query("UPDATE NxInventory i SET i.quantityAllocated = COALESCE(i.quantityAllocated, 0) + :qty, " +
           "i.version = i.version + 1 " +
           "WHERE i.id = :id " +
           "AND (COALESCE(i.quantityOnHand, 0) - COALESCE(i.quantityAllocated, 0) - COALESCE(i.quantityReserved, 0) - COALESCE(i.safetyStock, 0)) >= :qty")
    int reserveAtomicByRow(@Param("id") UUID id, @Param("qty") int qty);

    /** Releases against ONE resolved inventory row. Mirrors {@link #reserveAtomicByRow}. */
    @Modifying
    @Query("UPDATE NxInventory i SET i.quantityAllocated = CASE WHEN COALESCE(i.quantityAllocated, 0) >= :qty THEN COALESCE(i.quantityAllocated, 0) - :qty ELSE 0 END, " +
           "i.version = i.version + 1 " +
           "WHERE i.id = :id")
    int releaseAtomicByRow(@Param("id") UUID id, @Param("qty") int qty);

    /** Moves units into the held bucket at ONE row, only if that many un-held units are on hand. */
    @Modifying
    @Query("UPDATE NxInventory i SET i.quantityDamaged = COALESCE(i.quantityDamaged, 0) + :qty, i.version = i.version + 1 " +
           "WHERE i.id = :id AND COALESCE(i.quantityOnHand, 0) - COALESCE(i.quantityDamaged, 0) >= :qty")
    int holdAtomicByRow(@Param("id") UUID id, @Param("qty") int qty);

    /** Writes held units off on-hand at ONE row, only if both on-hand and the held bucket cover it. */
    @Modifying
    @Query("UPDATE NxInventory i SET i.quantityOnHand = COALESCE(i.quantityOnHand, 0) - :qty, " +
           "i.quantityDamaged = COALESCE(i.quantityDamaged, 0) - :qty, i.version = i.version + 1 " +
           "WHERE i.id = :id AND COALESCE(i.quantityOnHand, 0) >= :qty AND COALESCE(i.quantityDamaged, 0) >= :qty")
    int writeOffHeldAtomicByRow(@Param("id") UUID id, @Param("qty") int qty);

    @Query("SELECT COALESCE(SUM(i.quantityOnHand), 0) FROM NxInventory i WHERE i.tenantId = :tenantId AND i.sku = :sku")
    Integer getTotalOnHand(@Param("tenantId") UUID tenantId, @Param("sku") String sku);

    /**
     * Tenant+SKU available-to-promise, summed across every holding row.
     *
     * <p>Subtracts {@code safetyStock} for the same reason as
     * {@link #reserveAtomicByRow}: ATP is the number customers and the allocator are both shown, so
     * it must not count stock the warehouse is holding back. Note the sum is over raw per-row
     * arithmetic without a per-row {@code MAX(..., 0)} clamp, so a row whose safety stock exceeds
     * its on-hand contributes a negative term. That is intentional — clamping per row would silently
     * invent allocatable stock on exactly the most oversold SKUs. The net figure is still correct
     * against the sum of what each node can actually promise.
     */
    @Query("SELECT COALESCE(SUM(COALESCE(i.quantityOnHand, 0) - COALESCE(i.quantityAllocated, 0) - COALESCE(i.quantityReserved, 0) - COALESCE(i.safetyStock, 0)), 0) FROM NxInventory i WHERE i.tenantId = :tenantId AND i.sku = :sku")
    Integer getAvailableToPromise(@Param("tenantId") UUID tenantId, @Param("sku") String sku);
}
