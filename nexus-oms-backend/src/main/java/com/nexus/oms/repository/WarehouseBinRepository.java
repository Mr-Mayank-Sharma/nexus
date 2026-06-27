package com.nexus.oms.repository;

import com.nexus.oms.entity.WarehouseBin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, UUID> {

    List<WarehouseBin> findByWarehouseId(UUID warehouseId);

    List<WarehouseBin> findByZoneId(UUID zoneId);

    List<WarehouseBin> findByWarehouseIdAndIsEmpty(UUID warehouseId, boolean isEmpty);

    List<WarehouseBin> findByWarehouseIdAndBinType(UUID warehouseId, String binType);

    Page<WarehouseBin> findByTenantId(UUID tenantId, Pageable pageable);

    long countByWarehouseIdAndIsEmpty(UUID warehouseId, boolean isEmpty);
}
