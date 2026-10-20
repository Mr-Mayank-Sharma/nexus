package com.nexus.oms.repository;

import com.nexus.oms.entity.WarehouseEquipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WarehouseEquipmentRepository extends JpaRepository<WarehouseEquipment, UUID> {

    List<WarehouseEquipment> findByWarehouseId(UUID warehouseId);

    Page<WarehouseEquipment> findByTenantId(UUID tenantId, Pageable pageable);

    List<WarehouseEquipment> findByEquipmentType(String equipmentType);

    List<WarehouseEquipment> findByStatus(String status);

    List<WarehouseEquipment> findByAssignedTo(UUID assignedTo);
}
