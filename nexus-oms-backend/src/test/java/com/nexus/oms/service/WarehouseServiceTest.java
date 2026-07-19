package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private WarehouseZoneRepository warehouseZoneRepository;
    @Mock
    private WarehouseBinRepository warehouseBinRepository;
    @Mock
    private WarehouseStaffRepository warehouseStaffRepository;
    @Mock
    private WarehouseEquipmentRepository warehouseEquipmentRepository;

    private WarehouseService warehouseService;
    private UUID tenantId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(warehouseRepository, warehouseZoneRepository,
                warehouseBinRepository, warehouseStaffRepository, warehouseEquipmentRepository);
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("admin", tenantId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllWarehouses_returnsPage() {
        Warehouse wh = new Warehouse();
        wh.setId(warehouseId);
        PageRequest pr = PageRequest.of(0, 10);
        when(warehouseRepository.findByTenantId(tenantId, pr)).thenReturn(new PageImpl<>(List.of(wh)));

        Page<Warehouse> result = warehouseService.getAllWarehouses(pr);

        assertEquals(1, result.getTotalElements());
        assertEquals(warehouseId, result.getContent().get(0).getId());
    }

    @Test
    void getWarehouse_found() {
        Warehouse wh = new Warehouse();
        wh.setId(warehouseId);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(wh));

        Warehouse result = warehouseService.getWarehouse(warehouseId);

        assertEquals(warehouseId, result.getId());
    }

    @Test
    void getWarehouse_notFound_throws() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> warehouseService.getWarehouse(warehouseId));
    }

    @Test
    void createWarehouse_setsTenantAndSaves() {
        Warehouse input = new Warehouse();
        input.setCode("WH-01");
        when(warehouseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Warehouse result = warehouseService.createWarehouse(input);

        assertEquals(tenantId, result.getTenantId());
        assertEquals("WH-01", result.getCode());
    }

    @Test
    void updateWarehouse_updatesNonNullFields() {
        Warehouse existing = new Warehouse();
        existing.setId(warehouseId);
        existing.setCode("OLD");
        existing.setName("Old Name");

        Warehouse updates = new Warehouse();
        updates.setName("New Name");

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(existing));
        when(warehouseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Warehouse result = warehouseService.updateWarehouse(warehouseId, updates);

        assertEquals("OLD", result.getCode());
        assertEquals("New Name", result.getName());
    }

    @Test
    void deleteWarehouse_withoutBins_succeeds() {
        Warehouse wh = new Warehouse();
        wh.setId(warehouseId);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(wh));
        when(warehouseBinRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());

        warehouseService.deleteWarehouse(warehouseId);

        verify(warehouseRepository).delete(wh);
    }

    @Test
    void deleteWarehouse_withBins_throws() {
        Warehouse wh = new Warehouse();
        wh.setId(warehouseId);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(wh));
        when(warehouseBinRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(new WarehouseBin()));

        assertThrows(IllegalStateException.class, () -> warehouseService.deleteWarehouse(warehouseId));
    }

    @Test
    void getZones() {
        WarehouseZone zone = new WarehouseZone();
        zone.setId(UUID.randomUUID());
        when(warehouseZoneRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(zone));

        List<WarehouseZone> result = warehouseService.getZones(warehouseId);

        assertEquals(1, result.size());
    }

    @Test
    void createZone() {
        WarehouseZone zone = new WarehouseZone();
        zone.setCode("ZONE-A");
        when(warehouseZoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseZone result = warehouseService.createZone(zone);

        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void updateZone() {
        WarehouseZone existing = new WarehouseZone();
        existing.setId(UUID.randomUUID());
        existing.setCode("OLD");
        WarehouseZone updates = new WarehouseZone();
        updates.setCode("NEW");
        when(warehouseZoneRepository.findById(any())).thenReturn(Optional.of(existing));
        when(warehouseZoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseZone result = warehouseService.updateZone(UUID.randomUUID(), updates);

        assertEquals("NEW", result.getCode());
    }

    @Test
    void getBins() {
        when(warehouseBinRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(new WarehouseBin()));
        assertEquals(1, warehouseService.getBins(warehouseId).size());
    }

    @Test
    void getEmptyBins() {
        when(warehouseBinRepository.findByWarehouseIdAndIsEmpty(warehouseId, true)).thenReturn(List.of(new WarehouseBin()));
        assertEquals(1, warehouseService.getEmptyBins(warehouseId).size());
    }

    @Test
    void createBin() {
        WarehouseBin bin = new WarehouseBin();
        bin.setCode("BIN-001");
        when(warehouseBinRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseBin result = warehouseService.createBin(bin);

        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void reserveBin() {
        WarehouseBin bin = new WarehouseBin();
        bin.setId(UUID.randomUUID());
        when(warehouseBinRepository.findById(any())).thenReturn(Optional.of(bin));
        when(warehouseBinRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseBin result = warehouseService.reserveBin(UUID.randomUUID());

        assertTrue(result.getIsReserved());
    }

    @Test
    void releaseBin() {
        WarehouseBin bin = new WarehouseBin();
        bin.setId(UUID.randomUUID());
        when(warehouseBinRepository.findById(any())).thenReturn(Optional.of(bin));
        when(warehouseBinRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseBin result = warehouseService.releaseBin(UUID.randomUUID());

        assertFalse(result.getIsReserved());
        assertTrue(result.getIsEmpty());
    }

    @Test
    void getStaff() {
        when(warehouseStaffRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(new WarehouseStaff()));
        assertEquals(1, warehouseService.getStaff(warehouseId).size());
    }

    @Test
    void createStaff() {
        WarehouseStaff staff = new WarehouseStaff();
        staff.setEmployeeCode("EMP-001");
        when(warehouseStaffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseStaff result = warehouseService.createStaff(staff);

        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void updateStaff() {
        WarehouseStaff existing = new WarehouseStaff();
        existing.setId(UUID.randomUUID());
        existing.setFirstName("Old");
        WarehouseStaff updates = new WarehouseStaff();
        updates.setFirstName("New");
        when(warehouseStaffRepository.findById(any())).thenReturn(Optional.of(existing));
        when(warehouseStaffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseStaff result = warehouseService.updateStaff(UUID.randomUUID(), updates);

        assertEquals("New", result.getFirstName());
    }

    @Test
    void incrementPickCount() {
        WarehouseStaff staff = new WarehouseStaff();
        staff.setId(UUID.randomUUID());
        staff.setItemsPickedToday(5);
        when(warehouseStaffRepository.findById(any())).thenReturn(Optional.of(staff));
        when(warehouseStaffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseStaff result = warehouseService.incrementPickCount(UUID.randomUUID());

        assertEquals(6, result.getItemsPickedToday());
    }

    @Test
    void incrementPickCount_fromNull() {
        WarehouseStaff staff = new WarehouseStaff();
        staff.setId(UUID.randomUUID());
        when(warehouseStaffRepository.findById(any())).thenReturn(Optional.of(staff));
        when(warehouseStaffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseStaff result = warehouseService.incrementPickCount(UUID.randomUUID());

        assertEquals(1, result.getItemsPickedToday());
    }

    @Test
    void getEquipment() {
        when(warehouseEquipmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(new WarehouseEquipment()));
        assertEquals(1, warehouseService.getEquipment(warehouseId).size());
    }

    @Test
    void createEquipment() {
        WarehouseEquipment eq = WarehouseEquipment.builder()
                .code("FL-01").equipmentType("FORKLIFT").build();
        when(warehouseEquipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseEquipment result = warehouseService.createEquipment(eq);

        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void updateEquipmentStatus() {
        WarehouseEquipment eq = new WarehouseEquipment();
        eq.setId(UUID.randomUUID());
        when(warehouseEquipmentRepository.findById(any())).thenReturn(Optional.of(eq));
        when(warehouseEquipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WarehouseEquipment result = warehouseService.updateEquipmentStatus(UUID.randomUUID(), "MAINTENANCE");

        assertEquals("MAINTENANCE", result.getStatus());
    }

    @Test
    void getWarehouseSummary() {
        when(warehouseBinRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(new WarehouseBin(), new WarehouseBin()));
        when(warehouseBinRepository.countByWarehouseIdAndIsEmpty(warehouseId, true)).thenReturn(1L);
        WarehouseStaff activeStaff = new WarehouseStaff();
        activeStaff.setIsActive(true);
        when(warehouseStaffRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(activeStaff, new WarehouseStaff()));
        WarehouseEquipment availEq = new WarehouseEquipment();
        availEq.setStatus("AVAILABLE");
        when(warehouseEquipmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(availEq));

        var summary = warehouseService.getWarehouseSummary(warehouseId);

        assertEquals(2L, summary.get("totalBins"));
        assertEquals(1L, summary.get("emptyBins"));
        assertEquals(1L, summary.get("occupiedBins"));
        assertEquals(2L, summary.get("totalStaff"));
        assertEquals(1L, summary.get("activeStaff"));
        assertEquals(1L, summary.get("totalEquipment"));
        assertEquals(1L, summary.get("availableEquipment"));
        assertEquals(50.0, summary.get("capacityUtilization"));
    }

    @Test
    void getWarehouseSummary_noBins() {
        when(warehouseBinRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());
        when(warehouseStaffRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());
        when(warehouseEquipmentRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());

        var summary = warehouseService.getWarehouseSummary(warehouseId);

        assertEquals(0.0, summary.get("capacityUtilization"));
    }
}
