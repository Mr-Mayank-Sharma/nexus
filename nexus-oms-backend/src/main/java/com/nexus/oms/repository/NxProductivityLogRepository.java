package com.nexus.oms.repository;

import com.nexus.oms.entity.NxProductivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NxProductivityLogRepository extends JpaRepository<NxProductivityLog, UUID> {
    List<NxProductivityLog> findByStaffIdAndLoggedAtBetween(UUID staffId, LocalDateTime start, LocalDateTime end);
    List<NxProductivityLog> findByWarehouseIdAndLoggedAtBetween(UUID warehouseId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT p.staffId, AVG(p.itemsPerHour), AVG(p.qualityScore), AVG(p.vsStandardPct) " +
           "FROM NxProductivityLog p WHERE p.warehouseId = :warehouseId AND p.loggedAt BETWEEN :start AND :end " +
           "GROUP BY p.staffId ORDER BY AVG(p.itemsPerHour) DESC")
    List<Object[]> aggregateByStaff(@Param("warehouseId") UUID warehouseId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("SELECT p.taskType, AVG(p.itemsPerHour), COUNT(p.id) " +
           "FROM NxProductivityLog p WHERE p.warehouseId = :warehouseId AND p.loggedAt BETWEEN :start AND :end " +
           "GROUP BY p.taskType")
    List<Object[]> aggregateByTaskType(@Param("warehouseId") UUID warehouseId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
