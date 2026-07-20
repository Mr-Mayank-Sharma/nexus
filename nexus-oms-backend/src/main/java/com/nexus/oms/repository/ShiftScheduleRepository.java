package com.nexus.oms.repository;

import com.nexus.oms.entity.NxShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ShiftScheduleRepository extends JpaRepository<NxShiftSchedule, UUID> {

    List<NxShiftSchedule> findByTenantId(UUID tenantId);

    List<NxShiftSchedule> findByWarehouseIdAndShiftDate(UUID warehouseId, LocalDate shiftDate);

    List<NxShiftSchedule> findByStaffIdAndShiftDate(UUID staffId, LocalDate shiftDate);

    List<NxShiftSchedule> findByWarehouseIdAndShiftDateAndStatus(UUID warehouseId, LocalDate shiftDate, String status);
}
