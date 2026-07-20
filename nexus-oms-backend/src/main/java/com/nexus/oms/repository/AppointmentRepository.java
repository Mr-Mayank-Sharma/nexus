package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<NxAppointment, UUID> {

    List<NxAppointment> findByTenantId(UUID tenantId);

    List<NxAppointment> findByWarehouseId(UUID warehouseId);

    List<NxAppointment> findByWarehouseIdAndStatus(UUID warehouseId, String status);

    List<NxAppointment> findByDockDoorId(UUID dockDoorId);

    List<NxAppointment> findByWarehouseIdAndEstimatedArrivalBetween(
            UUID warehouseId, LocalDateTime start, LocalDateTime end);

    List<NxAppointment> findByWarehouseIdAndStatusIn(UUID warehouseId, Collection<String> statuses);

    List<NxAppointment> findByTenantIdAndStatus(UUID tenantId, String status);
}
