package com.nexus.oms.service;

import com.nexus.oms.dto.ImportResult;
import com.nexus.oms.entity.ImportHistory;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Inventory import is location-aware: (tenant, sku, warehouse) is the natural key (V70). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericImportInventoryTest {

    @Mock InventoryLedger inventoryLedger;
    @Mock ImportHistoryRepository importHistoryRepository;
    @Mock ImportRecordLogRepository importRecordLogRepository;
    @Mock OrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock AddressRepository addressRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock ReturnRepository returnRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock PlatformTransactionManager transactionManager;

    private GenericImportService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID wh1 = UUID.randomUUID();
    private final UUID wh2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(tenantId);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(importHistoryRepository.save(any(ImportHistory.class))).thenAnswer(i -> {
            ImportHistory h = i.getArgument(0);
            if (h.getId() == null) h.setId(UUID.randomUUID());
            return h;
        });
        when(inventoryRepository.save(any(NxInventory.class))).thenAnswer(i -> i.getArgument(0));
        when(warehouseRepository.findByTenantIdAndCode(tenantId, "WH-01")).thenReturn(Optional.of(warehouse(wh1)));
        when(warehouseRepository.findByTenantIdAndCode(tenantId, "WH-02")).thenReturn(Optional.of(warehouse(wh2)));
        when(warehouseRepository.findByTenantIdAndCode(tenantId, "NOPE")).thenReturn(Optional.empty());
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(any(), any(), any())).thenReturn(Optional.empty());
        when(inventoryRepository.findByTenantIdAndSkuAndNodeIdIsNull(any(), any())).thenReturn(Optional.empty());
        service = new GenericImportService(importHistoryRepository, importRecordLogRepository, orderRepository,
                customerRepository, productRepository, orderItemRepository, addressRepository, inventoryRepository,
                shipmentRepository, returnRepository, supplierRepository, purchaseOrderRepository, invoiceRepository,
                warehouseRepository, transactionManager, inventoryLedger);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sameSkuAtTwoWarehouses_CreatesTwoLocationRows() {
        ImportResult result = service.importFile("inventory", csv("sku,quantity,warehouse\nGLOVES,100,WH-01\nGLOVES,40,WH-02\n"), "csv", "UPSERT");

        assertEquals(2, result.getSuccessCount(), result.getErrors().toString());
        ArgumentCaptor<NxInventory> saved = ArgumentCaptor.forClass(NxInventory.class);
        verify(inventoryRepository, times(2)).save(saved.capture());
        assertEquals(List.of(wh1, wh2), saved.getAllValues().stream().map(NxInventory::getNodeId).toList());
    }

    @Test
    void upsert_UpdatesTheExistingLocationRowInsteadOfInserting() {
        NxInventory existing = NxInventory.builder().id(UUID.randomUUID()).tenantId(tenantId).sku("GLOVES")
                .nodeId(wh1).quantityOnHand(5).build();
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, "GLOVES", wh1)).thenReturn(Optional.of(existing));

        ImportResult result = service.importFile("inventory", csv("sku,quantity,warehouse\nGLOVES,250,WH-01\n"), "csv", "UPSERT");

        assertEquals(1, result.getSuccessCount());
        verify(inventoryRepository).save(same(existing));
        assertEquals(250, existing.getQuantityOnHand());
    }

    @Test
    void unknownWarehouseCode_IsAnErrorAndSavesNothing() {
        ImportResult result = service.importFile("inventory", csv("sku,quantity,warehouse\nGLOVES,10,NOPE\n"), "csv", "UPSERT");

        assertEquals(0, result.getSuccessCount());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void noWarehouseColumn_UsesTenantLevelRow() {
        ImportResult result = service.importFile("inventory", csv("sku,quantity\nGLOVES,10\n"), "csv", "UPSERT");

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<NxInventory> saved = ArgumentCaptor.forClass(NxInventory.class);
        verify(inventoryRepository).save(saved.capture());
        assertNull(saved.getValue().getNodeId());
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "inventory.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }

    private static Warehouse warehouse(UUID id) {
        Warehouse w = new Warehouse();
        w.setId(id);
        return w;
    }
}
