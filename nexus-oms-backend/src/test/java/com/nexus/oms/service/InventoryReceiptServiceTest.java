package com.nexus.oms.service;

import com.nexus.oms.dto.InventoryReceiptRequest;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxInventoryReceipt;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxInventoryReceiptRepository;
import com.nexus.oms.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReceiptServiceTest {

    @Mock
    private NxInventoryReceiptRepository receiptRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryReceiptService inventoryReceiptService;
    private UUID tenantId;
    private UUID receiptId;

    @BeforeEach
    void setUp() {
        inventoryReceiptService = new InventoryReceiptService(receiptRepository, inventoryRepository);
        tenantId = UUID.randomUUID();
        receiptId = UUID.randomUUID();
    }

    @Test
    void getReceipts_withStatus() {
        Page<NxInventoryReceipt> page = new PageImpl<>(List.of(new NxInventoryReceipt()));
        when(receiptRepository.findByTenantIdAndStatus(tenantId, "PENDING", Pageable.unpaged())).thenReturn(page);

        assertSame(page, inventoryReceiptService.getReceipts(tenantId, "PENDING", Pageable.unpaged()));
    }

    @Test
    void getReceipts_withoutStatus() {
        Page<NxInventoryReceipt> page = new PageImpl<>(List.of(new NxInventoryReceipt()));
        when(receiptRepository.findByTenantId(tenantId, Pageable.unpaged())).thenReturn(page);

        assertSame(page, inventoryReceiptService.getReceipts(tenantId, null, Pageable.unpaged()));
    }

    @Test
    void getReceipt_found() {
        NxInventoryReceipt receipt = new NxInventoryReceipt();
        receipt.setId(receiptId);
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

        assertSame(receipt, inventoryReceiptService.getReceipt(receiptId));
    }

    @Test
    void getReceipt_notFound_throws() {
        when(receiptRepository.findById(receiptId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> inventoryReceiptService.getReceipt(receiptId));
    }

    @Test
    void createReceipt() {
        UUID nodeId = UUID.randomUUID();
        InventoryReceiptRequest req = new InventoryReceiptRequest();
        req.setNodeId(nodeId);
        req.setReceiptType("PURCHASE_ORDER");
        req.setReferenceNumber("PO-123");
        req.setSku("SKU-001");
        req.setProductName("Test Product");
        req.setQuantity(100);
        req.setUnitCost(new BigDecimal("9.99"));
        req.setLotNumber("LOT-001");
        req.setExpiryDate(LocalDateTime.of(2027, 1, 1, 0, 0));

        when(receiptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxInventoryReceipt result = inventoryReceiptService.createReceipt(tenantId, req);

        assertEquals(tenantId, result.getTenantId());
        assertEquals(nodeId, result.getNodeId());
        assertEquals("PURCHASE_ORDER", result.getReceiptType());
        assertEquals("PO-123", result.getReferenceNumber());
        assertEquals("SKU-001", result.getSku());
        assertEquals(100, result.getQuantity());
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void receiveInventory_newInventory() {
        NxInventoryReceipt receipt = new NxInventoryReceipt();
        receipt.setId(receiptId);
        receipt.setTenantId(tenantId);
        receipt.setSku("SKU-001");
        receipt.setNodeId(UUID.randomUUID());
        receipt.setQuantity(50);
        receipt.setLotNumber("LOT-001");
        receipt.setStatus("PENDING");

        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(receiptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxInventoryReceipt result = inventoryReceiptService.receiveInventory(receiptId, "Alice");

        assertEquals("RECEIVED", result.getStatus());
        assertEquals("Alice", result.getReceivedBy());
        assertNotNull(result.getReceivedAt());
        verify(inventoryRepository).save(argThat(inv ->
                inv.getQuantityOnHand() == 50 &&
                inv.getSku().equals("SKU-001") &&
                "LOT-001".equals(inv.getLotNumber())
        ));
    }

    @Test
    void receiveInventory_existingInventory() {
        UUID nodeId = UUID.randomUUID();
        NxInventoryReceipt receipt = new NxInventoryReceipt();
        receipt.setId(receiptId);
        receipt.setTenantId(tenantId);
        receipt.setSku("SKU-001");
        receipt.setNodeId(nodeId);
        receipt.setQuantity(30);
        receipt.setLotNumber("LOT-002");
        receipt.setStatus("PENDING");

        NxInventory existing = new NxInventory();
        existing.setQuantityOnHand(100);
        existing.setLotNumber("LOT-001");

        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, "SKU-001", nodeId))
                .thenReturn(Optional.of(existing));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(receiptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        inventoryReceiptService.receiveInventory(receiptId, "Bob");

        assertEquals(130, existing.getQuantityOnHand());
        assertEquals("LOT-002", existing.getLotNumber());
    }

    @Test
    void receiveInventory_alreadyReceived_throws() {
        NxInventoryReceipt receipt = new NxInventoryReceipt();
        receipt.setId(receiptId);
        receipt.setStatus("RECEIVED");

        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

        assertThrows(IllegalStateException.class,
                () -> inventoryReceiptService.receiveInventory(receiptId, "Alice"));
    }

    @Test
    void deleteReceipt() {
        inventoryReceiptService.deleteReceipt(receiptId);
        verify(receiptRepository).deleteById(receiptId);
    }
}
