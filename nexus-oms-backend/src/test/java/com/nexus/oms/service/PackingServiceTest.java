package com.nexus.oms.service;

import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackingServiceTest {

    @Mock
    private PackageRepository packageRepository;
    @Mock
    private WarehouseStaffRepository warehouseStaffRepository;

    private PackingService packingService;
    private UUID tenantId;
    private UUID packageId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        packingService = new PackingService(packageRepository, warehouseStaffRepository);
        tenantId = UUID.randomUUID();
        packageId = UUID.randomUUID();
    }

    @Test
    void getPackages() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findByTenantId(tenantId)).thenReturn(List.of(pkg));

        List<NxPackage> result = packingService.getPackages(tenantId);

        assertEquals(1, result.size());
    }

    @Test
    void getPackagesByStatus() {
        when(packageRepository.findByTenantIdAndStatus(tenantId, "PENDING_PACK")).thenReturn(List.of(new NxPackage()));

        assertEquals(1, packingService.getPackagesByStatus(tenantId, "PENDING_PACK").size());
    }

    @Test
    void getPackage_found() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));

        NxPackage result = packingService.getPackage(packageId);

        assertEquals(packageId, result.getId());
    }

    @Test
    void getPackage_notFound_throws() {
        when(packageRepository.findById(packageId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> packingService.getPackage(packageId));
    }

    @Test
    void createPackage_setsPendingPack() {
        NxPackage input = new NxPackage();
        input.setTrackingNumber("TRACK-001");
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.createPackage(input);

        assertEquals("PENDING_PACK", result.getStatus());
        assertEquals("TRACK-001", result.getTrackingNumber());
    }

    @Test
    void startPacking() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.startPacking(packageId);

        assertEquals("PACKING", result.getStatus());
    }

    @Test
    void addItem_appendsJson() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        pkg.setItems("[]");
        pkg.setItemCount(0);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.addItem(packageId, "{\"sku\":\"A\"}");

        assertTrue(result.getItems().contains("sku"));
        assertEquals(1, result.getItemCount());
    }

    @Test
    void addItem_withNullItems() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        pkg.setItems(null);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.addItem(packageId, "{\"sku\":\"B\"}");

        assertNotNull(result.getItems());
        assertEquals(1, result.getItemCount());
    }

    @Test
    void addItem_withBlankItems() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        pkg.setItems("   ");
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.addItem(packageId, "{}");

        assertTrue(result.getItems().startsWith("["));
        assertEquals(1, result.getItemCount());
    }

    @Test
    void completePacking() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.completePacking(packageId, "packer-1");

        assertEquals("PACKED", result.getStatus());
        assertEquals("packer-1", result.getPackedBy());
        assertNotNull(result.getPackedAt());
    }

    @Test
    void generateLabel() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.generateLabel(packageId, UUID.randomUUID().toString(),
                "UPS", "GROUND", "1Z999AA10123456784", "https://labels.test");

        assertEquals("LABELED", result.getStatus());
        assertEquals("UPS", result.getCarrierName());
    }

    @Test
    void shipPackage() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.shipPackage(packageId);

        assertEquals("SHIPPED", result.getStatus());
        assertNotNull(result.getShippedAt());
    }

    @Test
    void voidPackage() {
        NxPackage pkg = new NxPackage();
        pkg.setId(packageId);
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxPackage result = packingService.voidPackage(packageId);

        assertEquals("VOIDED", result.getStatus());
    }

    @Test
    void getDashboardKPIs() {
        when(packageRepository.countByTenantIdAndStatus(tenantId, "PENDING_PACK")).thenReturn(1L);
        when(packageRepository.countByTenantIdAndStatus(tenantId, "PACKING")).thenReturn(2L);
        when(packageRepository.countByTenantIdAndStatus(tenantId, "PACKED")).thenReturn(3L);
        when(packageRepository.countByTenantIdAndStatus(tenantId, "SHIPPED")).thenReturn(4L);

        Map<String, Object> kpis = packingService.getDashboardKPIs(tenantId);

        assertEquals(1L, kpis.get("pendingPack"));
        assertEquals(2L, kpis.get("packing"));
        assertEquals(3L, kpis.get("packed"));
        assertEquals(4L, kpis.get("shipped"));
    }
}
