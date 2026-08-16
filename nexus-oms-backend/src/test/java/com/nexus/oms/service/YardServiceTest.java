package com.nexus.oms.service;

import com.nexus.oms.entity.NxAppointment;
import com.nexus.oms.entity.NxAsn;
import com.nexus.oms.entity.NxDockDoor;
import com.nexus.oms.entity.NxYardLocation;
import com.nexus.oms.repository.AppointmentRepository;
import com.nexus.oms.repository.DockDoorRepository;
import com.nexus.oms.repository.WarehouseRepository;
import com.nexus.oms.repository.YardLocationRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YardServiceTest {

    @Mock private DockDoorRepository dockDoorRepository;
    @Mock private YardLocationRepository yardLocationRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private WarehouseRepository warehouseRepository;

    private YardService yardService;
    private UUID tenantId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        yardService = new YardService(dockDoorRepository, yardLocationRepository, appointmentRepository, warehouseRepository);
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxDockDoor door(UUID id, String status) {
        return NxDockDoor.builder().id(id).tenantId(tenantId).warehouseId(warehouseId)
                .doorNumber("D1").doorType("INBOUND").status(status).build();
    }

    private NxYardLocation yardLoc(UUID id, String status, int capacity, int occupancy) {
        return NxYardLocation.builder().id(id).tenantId(tenantId).warehouseId(warehouseId)
                .locationCode("Y1").locationType("TRAILER").status(status).capacity(capacity)
                .currentOccupancy(occupancy).zone("A").build();
    }

    private NxAppointment appointment(UUID id, String status) {
        return NxAppointment.builder().id(id).tenantId(tenantId).warehouseId(warehouseId)
                .type("INBOUND").status(status).carrierName("FedEx")
                .estimatedArrival(LocalDateTime.now().plusHours(2)).build();
    }

    @Test
    void assignVehicleToDoor_occupiesAvailableDoor() {
        NxDockDoor available = door(UUID.randomUUID(), "AVAILABLE");
        when(dockDoorRepository.findById(available.getId())).thenReturn(java.util.Optional.of(available));
        when(dockDoorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID vehicleId = UUID.randomUUID();
        UUID aptId = UUID.randomUUID();

        NxDockDoor result = yardService.assignVehicleToDoor(available.getId(), vehicleId, aptId);

        assertEquals("OCCUPIED", result.getStatus());
        assertEquals(vehicleId, result.getCurrentVehicleId());
        assertEquals(aptId, result.getCurrentAppointmentId());
    }

    @Test
    void assignVehicleToDoor_rejectsMaintenanceDoor() {
        NxDockDoor maintenance = door(UUID.randomUUID(), "MAINTENANCE");
        when(dockDoorRepository.findById(maintenance.getId())).thenReturn(java.util.Optional.of(maintenance));

        assertThrows(IllegalStateException.class,
                () -> yardService.assignVehicleToDoor(maintenance.getId(), UUID.randomUUID(), null));
    }

    @Test
    void requestAppointment_generatesNumberAndSetsRequested() {
        NxAppointment apt = appointment(UUID.randomUUID(), null);
        when(appointmentRepository.findAll()).thenReturn(List.of());
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAppointment saved = yardService.requestAppointment(apt);

        assertEquals("REQUESTED", saved.getStatus());
        assertTrue(saved.getAppointmentNumber().startsWith("APT-"));
    }

    @Test
    void confirmAppointment_autoAssignsDockDoorAndYardLocation() {
        NxAppointment apt = appointment(UUID.randomUUID(), "REQUESTED");
        NxDockDoor door = door(UUID.randomUUID(), "AVAILABLE");
        NxYardLocation loc = yardLoc(UUID.randomUUID(), "AVAILABLE", 5, 0);
        when(appointmentRepository.findById(apt.getId())).thenReturn(java.util.Optional.of(apt));
        when(dockDoorRepository.findByWarehouseIdAndStatus(warehouseId, "AVAILABLE")).thenReturn(List.of(door));
        when(yardLocationRepository.findByWarehouseIdAndStatus(warehouseId, "AVAILABLE")).thenReturn(List.of(loc));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAppointment confirmed = yardService.confirmAppointment(apt.getId());

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(door.getId(), confirmed.getDockDoorId());
        assertEquals(loc.getId(), confirmed.getYardLocationId());
    }

    @Test
    void checkInAppointment_occupiesDoorAndIncrementsYardOccupancy() {
        NxAppointment apt = appointment(UUID.randomUUID(), "CONFIRMED");
        NxDockDoor door = door(UUID.randomUUID(), "AVAILABLE");
        NxYardLocation loc = yardLoc(UUID.randomUUID(), "AVAILABLE", 5, 1);
        apt.setDockDoorId(door.getId());
        apt.setYardLocationId(loc.getId());
        when(appointmentRepository.findById(apt.getId())).thenReturn(java.util.Optional.of(apt));
        when(dockDoorRepository.findById(door.getId())).thenReturn(java.util.Optional.of(door));
        when(yardLocationRepository.findById(loc.getId())).thenReturn(java.util.Optional.of(loc));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dockDoorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(yardLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAppointment checkedIn = yardService.checkInAppointment(apt.getId(), "guard-1");

        assertEquals("CHECKED_IN", checkedIn.getStatus());
        assertNotNull(checkedIn.getActualArrival());
        assertEquals("OCCUPIED", door.getStatus());
        assertEquals(2, loc.getCurrentOccupancy());
    }

    @Test
    void completeAppointment_releasesDockDoorAndYard() {
        NxAppointment apt = appointment(UUID.randomUUID(), "IN_PROGRESS");
        NxDockDoor door = door(UUID.randomUUID(), "OCCUPIED");
        NxYardLocation loc = yardLoc(UUID.randomUUID(), "OCCUPIED", 5, 1);
        apt.setDockDoorId(door.getId());
        apt.setYardLocationId(loc.getId());
        when(appointmentRepository.findById(apt.getId())).thenReturn(java.util.Optional.of(apt));
        when(dockDoorRepository.findById(door.getId())).thenReturn(java.util.Optional.of(door));
        when(yardLocationRepository.findById(loc.getId())).thenReturn(java.util.Optional.of(loc));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dockDoorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(yardLocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAppointment completed = yardService.completeAppointment(apt.getId(), "supervisor");

        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getActualDeparture());
        assertEquals("AVAILABLE", door.getStatus());
        assertEquals(0, loc.getCurrentOccupancy());
    }

    @Test
    void cancelAppointment_rejectsAlreadyCompleted() {
        NxAppointment apt = appointment(UUID.randomUUID(), "COMPLETED");
        when(appointmentRepository.findById(apt.getId())).thenReturn(java.util.Optional.of(apt));

        assertThrows(IllegalStateException.class, () -> yardService.cancelAppointment(apt.getId()));
    }

    @Test
    void getDockUtilization_computesPercentages() {
        when(dockDoorRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(
                door(UUID.randomUUID(), "OCCUPIED"),
                door(UUID.randomUUID(), "AVAILABLE"),
                door(UUID.randomUUID(), "MAINTENANCE")));

        Map<String, Object> util = yardService.getDockUtilization(warehouseId);

        assertEquals(3L, util.get("total"));
        assertEquals(1L, util.get("occupied"));
        assertEquals(1L, util.get("available"));
        assertEquals(33.33, util.get("utilizationPercent"));
    }

    @Test
    void linkAsnToAppointment_setsAsnAndEdiDocument() {
        NxAppointment apt = appointment(UUID.randomUUID(), "REQUESTED");
        UUID asnId = UUID.randomUUID();
        UUID ediDocId = UUID.randomUUID();
        when(appointmentRepository.findById(apt.getId())).thenReturn(java.util.Optional.of(apt));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAppointment linked = yardService.linkAsnToAppointment(apt.getId(), asnId, ediDocId);

        assertEquals(asnId, linked.getAsnId());
        assertEquals(ediDocId, linked.getEdiDocumentId());
    }

    @Test
    void createAppointmentFromAsn_buildsInboundAppointment() {
        UUID asnId = UUID.randomUUID();
        NxAsn asn = NxAsn.builder().id(asnId).tenantId(tenantId)
                .asnNumber("SN-1001").purchaseOrderNumber("PO-12345")
                .carrierCode("FDX").supplierName("Acme Corp").build();
        when(appointmentRepository.findAll()).thenReturn(List.of());
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxAppointment apt = yardService.createAppointmentFromAsn(warehouseId, asn, null);

        assertEquals("INBOUND", apt.getType());
        assertEquals("REQUESTED", apt.getStatus());
        assertEquals("FDX", apt.getCarrierCode());
        assertEquals("Acme Corp", apt.getCarrierName());
        assertEquals("PO-12345", apt.getPoNumbers());
        assertEquals(asnId, apt.getAsnId());
    }

    @Test
    void getAppointmentStats_countsStatusesAndAverageTurnaround() {
        NxAppointment a = appointment(UUID.randomUUID(), "COMPLETED");
        a.setActualArrival(LocalDateTime.now().minusHours(2));
        a.setActualDeparture(LocalDateTime.now());
        NxAppointment b = appointment(UUID.randomUUID(), "NO_SHOW");
        when(appointmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(a, b));

        Map<String, Object> stats = yardService.getAppointmentStats(warehouseId);

        assertEquals(2L, stats.get("total"));
        assertEquals(1L, stats.get("completed"));
        assertEquals(1L, stats.get("noShows"));
        assertEquals(120L, stats.get("avgTurnaroundMinutes"));
    }
}
