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

    List<Warehouse> findByTenantIdAndStatus(UUID tenantId, String status);

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
