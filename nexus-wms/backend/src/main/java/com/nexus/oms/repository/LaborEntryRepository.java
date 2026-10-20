package com.nexus.oms.repository;

import com.nexus.oms.entity.NxLaborEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LaborEntryRepository extends JpaRepository<NxLaborEntry, UUID> {

    List<NxLaborEntry> findByTenantId(UUID tenantId);

    List<NxLaborEntry> findByStaffIdAndStatus(UUID staffId, String status);

    List<NxLaborEntry> findByTenantIdAndWarehouseId(UUID tenantId, UUID warehouseId);

    @Query("SELECT e FROM NxLaborEntry e WHERE e.warehouseId = :warehouseId AND CAST(e.clockedInAt AS LocalDate) = CAST(:shiftDate AS LocalDate)")
    List<NxLaborEntry> findByWarehouseIdAndShiftDate(@Param("warehouseId") UUID warehouseId, @Param("shiftDate") String shiftDate);

    @Query("SELECT e FROM NxLaborEntry e WHERE e.staffId = :staffId AND CAST(e.clockedInAt AS LocalDate) = CAST(:shiftDate AS LocalDate)")
    List<NxLaborEntry> findByStaffIdAndShiftDate(@Param("staffId") UUID staffId, @Param("shiftDate") String shiftDate);

    List<NxLaborEntry> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxLaborEntry> findByWarehouseIdAndClockedInAtBetween(UUID warehouseId, LocalDateTime start, LocalDateTime end);
}
