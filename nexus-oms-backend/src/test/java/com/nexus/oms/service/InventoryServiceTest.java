package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private com.nexus.oms.service.bigcommerce.BigCommerceInventorySyncService bigCommerceInventorySyncService;

    @Mock
    private com.nexus.oms.repository.InventoryMovementRepository movementRepository;
    @Mock
    private InventoryLedger inventoryLedger;

    private InventoryService inventoryService;
    private UUID tenantId;
    private NxInventory testInventory;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, bigCommerceInventorySyncService, movementRepository, inventoryLedger,
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        tenantId = UUID.randomUUID();
        testInventory = NxInventory.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .sku("TEST-SKU-001")
                .quantityOnHand(100)
                .quantityReserved(10)
                .build();
    }

    @Test
    void testGetInventoryByTenant_ReturnsList() {
        when(inventoryRepository.findByTenantId(tenantId)).thenReturn(List.of(testInventory));
        List<NxInventory> result = inventoryService.getInventoryByTenant(tenantId);
        assertEquals(1, result.size());
        assertEquals("TEST-SKU-001", result.get(0).getSku());
        verify(inventoryRepository).findByTenantId(tenantId);
    }

    @Test
    void testGetBySku_WhenExists_ReturnsInventory() {
        when(inventoryRepository.findByTenantIdAndSku(tenantId, "TEST-SKU-001"))
                .thenReturn(List.of(testInventory));
        NxInventory result = inventoryService.getBySku(tenantId, "TEST-SKU-001");
        assertEquals("TEST-SKU-001", result.getSku());
    }

    @Test
    void testGetBySku_WhenNotExists_ThrowsException() {
        when(inventoryRepository.findByTenantIdAndSku(tenantId, "NONEXISTENT"))
                .thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,
                () -> inventoryService.getBySku(tenantId, "NONEXISTENT"));
    }

    @Test
    void testAdjustInventory_IncreasesQuantity() {
        when(inventoryRepository.findById(testInventory.getId())).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(NxInventory.class))).thenAnswer(i -> i.getArgument(0));
        NxInventory result = inventoryService.adjustInventory(testInventory.getId(), 50);
        assertEquals(150, result.getQuantityOnHand());
    }

    @Test
    void testAdjustInventory_DecreasesQuantity() {
        when(inventoryRepository.findById(testInventory.getId())).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(NxInventory.class))).thenAnswer(i -> i.getArgument(0));
        NxInventory result = inventoryService.adjustInventory(testInventory.getId(), -30);
        assertEquals(70, result.getQuantityOnHand());
    }

    @Test
    void testAdjustInventory_WhenInsufficient_ThrowsException() {
        when(inventoryRepository.findById(testInventory.getId())).thenReturn(Optional.of(testInventory));
        assertThrows(BadRequestException.class,
                () -> inventoryService.adjustInventory(testInventory.getId(), -200));
    }

    // ---------------------------------------------------------------------
    // Allocatable stock (T-17): onHand - allocated - reserved - safetyStock,
    // clamped at zero. These exercise the private available() through its
    // two public entry points, checkAvailability() and resolveStock().
    // ---------------------------------------------------------------------

    private NxInventory stock(Integer onHand, Integer allocated, Integer reserved, Integer safety) {
        return NxInventory.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .sku("TEST-SKU-001")
                .quantityOnHand(onHand)
                .quantityAllocated(allocated)
                .quantityReserved(reserved)
                .safetyStock(safety)
                .build();
    }

    private void givenSharedPool(NxInventory inv) {
        when(inventoryRepository.findByTenantIdAndSku(tenantId, "TEST-SKU-001"))
                .thenReturn(List.of(inv));
    }

    @Test
    void testCheckAvailability_WithholdsSafetyStock() {
        // 100 on hand - 10 allocated - 5 reserved - 20 safety = 65
        givenSharedPool(stock(100, 10, 5, 20));

        assertTrue(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 65));
        assertFalse(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 66),
                "the last 20 units are the safety buffer and must not be allocatable");
    }

    @Test
    void testCheckAvailability_WithholdsReservedStock() {
        // 50 on hand - 0 allocated - 30 reserved - 0 safety = 20
        givenSharedPool(stock(50, 0, 30, 0));

        assertTrue(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 20));
        assertFalse(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 21),
                "stock already reserved for an order must not be promised twice");
    }

    @Test
    void testCheckAvailability_ClampsNegativeAllocatableToZero() {
        // A fresh SKU carrying a global safety setting can be under-buffered
        // before first receipt; that must read as zero, never negative.
        givenSharedPool(stock(5, 0, 0, 20));

        assertFalse(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 1));
        assertEquals(0, inventoryService.resolveStock(tenantId, "TEST-SKU-001", null).available());
    }

    @Test
    void testCheckAvailability_NullQuantities_TreatedAsZero() {
        // Seed/legacy rows can carry explicit NULLs. Must not NPE.
        givenSharedPool(stock(null, null, null, null));

        assertFalse(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 1));
        assertEquals(0, inventoryService.resolveStock(tenantId, "TEST-SKU-001", null).available());
    }

    @Test
    void testResolveStock_NodeRow_WinsAndStillSubtractsSafetyStock() {
        UUID nodeId = UUID.randomUUID();
        NxInventory nodeRow = NxInventory.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .sku("TEST-SKU-001")
                .nodeId(nodeId)
                .quantityOnHand(40)
                .quantityAllocated(4)
                .quantityReserved(6)
                .safetyStock(10)
                .build();
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, "TEST-SKU-001", nodeId))
                .thenReturn(Optional.of(nodeRow));

        InventoryService.StockPosition pos =
                inventoryService.resolveStock(tenantId, "TEST-SKU-001", nodeId);

        // 40 - 4 - 6 - 10 = 20
        assertEquals(20, pos.available());
        assertFalse(pos.fromSharedPool(), "a node-specific row must be reported as such");
    }

    @Test
    void testResolveStock_NoInventory_ReportsZeroWithNoBackingRow() {
        when(inventoryRepository.findByTenantIdAndSku(tenantId, "MISSING-SKU")).thenReturn(List.of());

        InventoryService.StockPosition pos =
                inventoryService.resolveStock(tenantId, "MISSING-SKU", null);

        assertNull(pos.inventoryId());
        assertEquals(0, pos.available());
        assertTrue(pos.fromSharedPool());
    }

    @Test
    void testCheckAvailability_NonPositiveQty_AlwaysFalse() {
        // No stub: checkAvailability short-circuits on `qty > 0` and never reaches the repository.
        assertFalse(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, 0));
        assertFalse(inventoryService.checkAvailability(tenantId, "TEST-SKU-001", null, -5));
        verifyNoInteractions(inventoryRepository);
    }
}
