package com.nexus.oms.repository;

import com.nexus.oms.entity.NxDockDoor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DockDoorRepository extends JpaRepository<NxDockDoor, UUID> {

    List<NxDockDoor> findByTenantId(UUID tenantId);

    List<NxDockDoor> findByWarehouseId(UUID warehouseId);

    List<NxDockDoor> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxDockDoor> findByWarehouseIdAndDoorType(UUID warehouseId, String doorType);

    List<NxDockDoor> findByWarehouseIdAndCurrentVehicleIdIsNotNull(UUID warehouseId);
}
