package com.nexus.oms.repository;

import com.nexus.oms.entity.NxYardLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface YardLocationRepository extends JpaRepository<NxYardLocation, UUID> {

    List<NxYardLocation> findByTenantId(UUID tenantId);

    List<NxYardLocation> findByWarehouseId(UUID warehouseId);

    List<NxYardLocation> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxYardLocation> findByWarehouseIdAndLocationType(UUID warehouseId, String locationType);
}
