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

    private InventoryService inventoryService;
    private UUID tenantId;
    private NxInventory testInventory;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository);
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
}
