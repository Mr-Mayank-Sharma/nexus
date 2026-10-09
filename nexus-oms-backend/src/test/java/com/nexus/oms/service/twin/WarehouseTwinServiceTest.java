package com.nexus.oms.service.twin;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseTwinServiceTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock WarehouseZoneRepository zoneRepository;
    @Mock DockDoorRepository dockDoorRepository;
    @Mock YardLocationRepository yardLocationRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock NxTrailerRepository trailerRepository;
    @Mock WarehouseEquipmentRepository equipmentRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock ShipmentRepository shipmentRepository;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 9, 10, 0);
    private WarehouseTwinService service;
    private UUID tenantId;
    private UUID whId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new WarehouseTwinService(warehouseRepository, zoneRepository, dockDoorRepository, yardLocationRepository,
                appointmentRepository, trailerRepository, equipmentRepository, inventoryRepository, shipmentRepository, clock);
        tenantId = UUID.randomUUID();
        whId = UUID.randomUUID();
        Warehouse wh = new Warehouse();
        wh.setId(whId);
        wh.setTenantId(tenantId);
        wh.setCode("WH-01");
        wh.setName("Riverside Hub");
        wh.setCity("Newark");
        wh.setTotalCapacitySqm(new BigDecimal("1000"));
        wh.setUsedCapacitySqm(new BigDecimal("780"));
        when(warehouseRepository.findByIdAndTenantId(whId, tenantId)).thenReturn(Optional.of(wh));
        when(zoneRepository.findByWarehouseId(whId)).thenReturn(List.of());
        when(yardLocationRepository.findByWarehouseId(whId)).thenReturn(List.of());
        when(equipmentRepository.findByWarehouseId(whId)).thenReturn(List.of());
    }

    @Test
    void otherTenantsWarehouse_IsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.snapshot(UUID.randomUUID(), whId));
    }

    @Test
    void snapshot_AggregatesRealRecords() {
        NxDockDoor bay1 = NxDockDoor.builder().id(UUID.randomUUID()).doorNumber("Bay 1").status("OCCUPIED").build();
        NxDockDoor bay2 = NxDockDoor.builder().id(UUID.randomUUID()).doorNumber("Bay 2").status("AVAILABLE").build();
        when(dockDoorRepository.findByWarehouseId(whId)).thenReturn(List.of(bay1, bay2));

        NxAppointment atDoor = NxAppointment.builder().id(UUID.randomUUID()).type("OUTBOUND").status("IN_PROGRESS")
                .carrierName("WareTrack").trailerNumber("TRK-2051").dockDoorId(bay1.getId())
                .estimatedArrival(now.minusHours(1)).palletCount(6).build();
        NxAppointment arriving = NxAppointment.builder().id(UUID.randomUUID()).type("INBOUND").status("SCHEDULED")
                .carrierName("Bluepeak").trailerNumber("TRK-2287").estimatedArrival(now.plusMinutes(2)).build();
        when(appointmentRepository.findByWarehouseIdAndStatusIn(eq(whId), anyCollection())).thenReturn(List.of(atDoor, arriving));
        when(trailerRepository.findByWarehouseId(whId)).thenReturn(List.of(
                NxTrailer.builder().id(UUID.randomUUID()).trailerNumber("TRK-2051").status("DOCKED").build(),
                NxTrailer.builder().id(UUID.randomUUID()).trailerNumber("TRL-9").status("CHECKED_OUT").checkedOutAt(now).build()));

        when(inventoryRepository.findByTenantIdAndNodeId(tenantId, whId)).thenReturn(List.of(
                inv("HELMET", 82, 0, 10), inv("GLOVES", 5, 0, 20), inv("BOX-M", 1817, 1817, 0)));

        NxShipment onTime = NxShipment.builder().id(UUID.randomUUID()).status("DELIVERED").shippedAt(now.minusHours(2))
                .estimatedDelivery(now.minusDays(1)).actualDelivery(now.minusDays(1).minusHours(1)).createdAt(now.minusDays(2)).build();
        NxShipment late = NxShipment.builder().id(UUID.randomUUID()).status("DELIVERED")
                .estimatedDelivery(now.minusDays(3)).actualDelivery(now.minusDays(2)).createdAt(now.minusDays(4)).build();
        when(shipmentRepository.findTop50ByTenantIdAndOriginNodeIdOrderByCreatedAtDesc(tenantId, whId)).thenReturn(List.of(onTime, late));

        WarehouseTwinService.Snapshot snap = service.snapshot(tenantId, whId);

        assertEquals(78.0, snap.site().fillPct());
        assertEquals(2, snap.site().doorsTotal());
        assertEquals(1, snap.site().doorsOccupied());
        assertEquals(1904, snap.kpis().stockOnHandUnits());
        assertEquals(1, snap.kpis().trucksOnSite(), "only the in-progress truck is on site; the scheduled one is arriving");
        assertEquals(2, snap.kpis().arrivalsToday());
        assertEquals(1, snap.kpis().outboundShipmentsToday());
        assertEquals(50.0, snap.kpis().onTimeDeliveryPct30d());
        assertEquals(2, snap.trucks().size(), "checked-out trailer and the duplicate of TRK-2051 are excluded");
        assertEquals("Bay 1", snap.trucks().get(0).dockDoorNumber());
        assertEquals("OUTBOUND", snap.trucks().get(0).direction());
        assertEquals("TRK-2051", snap.docks().get(0).vehicleLabel());

        assertEquals("BOX-M", snap.topInventory().get(0).sku());
        assertEquals("OUT_OF_STOCK", snap.topInventory().get(0).stockStatus(), "fully allocated stock is not available");
        assertEquals("IN_STOCK", snap.topInventory().get(1).stockStatus());
        assertEquals("LOW_STOCK", snap.topInventory().get(2).stockStatus());
    }

    @Test
    void noDeliveries_OnTimeIsUnknownNotInvented() {
        when(dockDoorRepository.findByWarehouseId(whId)).thenReturn(List.of());
        when(appointmentRepository.findByWarehouseIdAndStatusIn(eq(whId), anyCollection())).thenReturn(List.of());
        when(trailerRepository.findByWarehouseId(whId)).thenReturn(List.of());
        when(inventoryRepository.findByTenantIdAndNodeId(tenantId, whId)).thenReturn(List.of());
        when(shipmentRepository.findTop50ByTenantIdAndOriginNodeIdOrderByCreatedAtDesc(tenantId, whId)).thenReturn(List.of());

        WarehouseTwinService.Snapshot snap = service.snapshot(tenantId, whId);

        assertNull(snap.kpis().onTimeDeliveryPct30d());
        assertNull(snap.kpis().onTimeSample30d());
    }

    @Test
    void fillPct_UnknownCapacity_IsNull() {
        assertNull(WarehouseTwinService.fillPct(null, new BigDecimal("10")));
        assertNull(WarehouseTwinService.fillPct(BigDecimal.ONE, BigDecimal.ZERO));
    }

    private NxInventory inv(String sku, int onHand, int allocated, int safety) {
        NxInventory i = new NxInventory();
        i.setSku(sku);
        i.setQuantityOnHand(onHand);
        i.setQuantityAllocated(allocated);
        i.setSafetyStock(safety);
        return i;
    }
}
