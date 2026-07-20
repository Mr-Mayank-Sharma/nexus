package com.nexus.oms.repository;

import com.nexus.oms.entity.NxLaborEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LaborEntryRepository extends JpaRepository<NxLaborEntry, UUID> {

    List<NxLaborEntry> findByTenantId(UUID tenantId);

    List<NxLaborEntry> findByStaffIdAndStatus(UUID staffId, String status);

    List<NxLaborEntry> findByTenantIdAndWarehouseId(UUID tenantId, UUID warehouseId);

    List<NxLaborEntry> findByWarehouseIdAndShiftDate(UUID warehouseId, String shiftDate);

    List<NxLaborEntry> findByStaffIdAndShiftDate(UUID staffId, String shiftDate);

    List<NxLaborEntry> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxLaborEntry> findByWarehouseIdAndClockedInAtBetween(UUID warehouseId, LocalDateTime start, LocalDateTime end);
}
