package com.nexus.oms.repository;

import com.nexus.oms.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Page<Warehouse> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Warehouse> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<Warehouse> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Warehouse> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Warehouse> findByTenantIdAndStatusIn(UUID tenantId, java.util.Collection<String> statuses);

    /** Statuses of a warehouse that can take work. The product uses both: ACTIVE (the default) and OPERATIONAL. */
    java.util.List<String> OPERATIONAL_STATUSES = java.util.List.of("ACTIVE", "OPERATIONAL");

    /**
     * Warehouses allocation, routing and waves may use: ACTIVE or OPERATIONAL, and not switched off.
     * Callers used to ask for exactly "ACTIVE", so a tenant whose warehouses are OPERATIONAL could
     * not allocate a single order ("No active warehouse nodes found").
     */
    default List<Warehouse> findOperational(UUID tenantId) {
        return findByTenantIdAndStatusIn(tenantId, OPERATIONAL_STATUSES).stream()
                .filter(w -> !Boolean.FALSE.equals(w.getIsActive()))
                .toList();
    }

    long countByTenantId(UUID tenantId);

    @Query("SELECT w FROM Warehouse w WHERE w.tenantId = :tenantId AND " +
           "(LOWER(w.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(w.type) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(w.status) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(w.city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(w.state) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(w.country) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Warehouse> search(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);
}
