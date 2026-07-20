package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSlottingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SlottingAssignmentRepository extends JpaRepository<NxSlottingAssignment, UUID> {

    List<NxSlottingAssignment> findByTenantId(UUID tenantId);

    List<NxSlottingAssignment> findByWarehouseId(UUID warehouseId);

    List<NxSlottingAssignment> findBySku(String sku);

    List<NxSlottingAssignment> findByBinId(UUID binId);

    List<NxSlottingAssignment> findByWarehouseIdAndVelocityClass(UUID warehouseId, String velocityClass);

    List<NxSlottingAssignment> findByWarehouseIdAndPickFrequencyLessThan(UUID warehouseId, Integer pickFrequency);

    List<NxSlottingAssignment> findByWarehouseIdOrderByPickFrequencyDesc(UUID warehouseId);
}
