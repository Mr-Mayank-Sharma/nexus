package com.nexus.oms.repository;

import com.nexus.oms.entity.WarehouseBin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, UUID> {

    List<WarehouseBin> findByWarehouseId(UUID warehouseId);

    List<WarehouseBin> findByZoneId(UUID zoneId);

    @Query("SELECT w FROM WarehouseBin w WHERE w.warehouseId = :warehouseId AND w.isEmpty = :isEmpty")
    List<WarehouseBin> findByWarehouseIdAndIsEmpty(@Param("warehouseId") UUID warehouseId, @Param("isEmpty") boolean isEmpty);

    List<WarehouseBin> findByWarehouseIdAndBinType(UUID warehouseId, String binType);

    Page<WarehouseBin> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT COUNT(w) FROM WarehouseBin w WHERE w.warehouseId = :warehouseId AND w.isEmpty = :isEmpty")
    long countByWarehouseIdAndIsEmpty(@Param("warehouseId") UUID warehouseId, @Param("isEmpty") boolean isEmpty);
}
