package com.nexus.oms.repository;

import com.nexus.oms.entity.WarehouseZone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WarehouseZoneRepository extends JpaRepository<WarehouseZone, UUID> {

    List<WarehouseZone> findByWarehouseId(UUID warehouseId);

    List<WarehouseZone> findByWarehouseIdAndZoneType(UUID warehouseId, String zoneType);

    Page<WarehouseZone> findByTenantId(UUID tenantId, Pageable pageable);
}
