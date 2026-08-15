package com.nexus.oms.service;

import com.nexus.oms.entity.PurchaseOrder;
import com.nexus.oms.entity.PurchaseOrderItem;
import com.nexus.oms.entity.PurchaseRequest;
import com.nexus.oms.entity.Supplier;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcurementServiceTest {

    @Mock private SupplierRepository supplierRepository;
    @Mock private SupplierContactRepository supplierContactRepository;
    @Mock private SupplierContractRepository supplierContractRepository;
    @Mock private PurchaseRequestRepository purchaseRequestRepository;
    @Mock private PurchaseRequestItemRepository purchaseRequestItemRepository;
    @Mock private RfqRepository rfqRepository;
    @Mock private RfqResponseRepository rfqResponseRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private SlottingService slottingService;

    private ProcurementService service;
    private UUID tenantId;
    private UUID poId;

    @BeforeEach
    void setUp() {
        service = new ProcurementService(supplierRepository, supplierContactRepository, supplierContractRepository,
                purchaseRequestRepository, purchaseRequestItemRepository, rfqRepository, rfqResponseRepository,
                purchaseOrderRepository, purchaseOrderItemRepository, slottingService);
        tenantId = UUID.randomUUID();
        poId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PurchaseOrder po() {
        return PurchaseOrder.builder().id(poId).tenantId(tenantId).status("DRAFT")
                .taxAmount(new BigDecimal("10.00")).shippingCost(new BigDecimal("5.00")).build();
    }

    private PurchaseOrderItem item(String sku, int ordered, int received, String totalPrice) {
        return PurchaseOrderItem.builder().id(UUID.randomUUID()).poId(poId).sku(sku)
                .productName("Item " + sku).quantityOrdered(ordered).quantityReceived(received)
                .totalPrice(totalPrice != null ? new BigDecimal(totalPrice) : null).build();
    }

    @Test
    void createSupplier_assignsGeneratedCodeWhenMissing() {
        Supplier s = Supplier.builder().tenantId(tenantId).companyName("Acme").build();
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Supplier saved = service.createSupplier(s);

        assertNotNull(saved.getSupplierCode());
        assertTrue(saved.getSupplierCode().startsWith("SUP-"));
        assertEquals(tenantId, saved.getTenantId());
    }

    @Test
    void createPurchaseOrder_computesSubtotalAndTotalFromItems() {
        PurchaseOrder po = po();
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(
                item("SKU-1", 10, 0, "100.00"),
                item("SKU-2", 5, 0, "25.00")));

        PurchaseOrder saved = service.createPurchaseOrder(po);

        assertEquals(new BigDecimal("125.00"), saved.getSubtotal());
        assertEquals(new BigDecimal("140.00"), saved.getTotalAmount()); // 125 + 10 tax + 5 shipping
        assertNotNull(saved.getPoNumber());
        assertNotNull(saved.getOrderDate());
    }

    @Test
    void receiveItems_marksFullyReceivedWhenAllQuantitiesMet() {
        PurchaseOrder po = po();
        PurchaseOrderItem sku1 = item("SKU-1", 10, 0, null);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(sku1));
        when(purchaseOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrder result = service.receiveItems(poId, List.of(Map.of(
                "sku", "SKU-1", "quantityReceived", 10)));

        assertEquals(10, sku1.getQuantityReceived());
        assertEquals(true, result.getIsFullyReceived());
    }

    @Test
    void receiveItems_partialReceiptLeavesPoNotFullyReceived() {
        PurchaseOrder po = po();
        PurchaseOrderItem sku1 = item("SKU-1", 10, 0, null);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(sku1));
        when(purchaseOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrder result = service.receiveItems(poId, List.of(Map.of(
                "sku", "SKU-1", "quantityReceived", 4)));

        assertEquals(4, sku1.getQuantityReceived());
        assertEquals(false, result.getIsFullyReceived());
    }

    @Test
    void receiveItems_unknownSkuThrowsBadRequest() {
        PurchaseOrder po = po();
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(item("SKU-1", 10, 0, null)));

        assertThrows(BadRequestException.class,
                () -> service.receiveItems(poId, List.of(Map.of("sku", "NOPE", "quantityReceived", 1))));
    }

    @Test
    void receiveItems_rejectsOverReceiptBeyondTolerance() {
        PurchaseOrder po = po();
        PurchaseOrderItem sku1 = item("SKU-1", 10, 0, null);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(sku1));

        // 10% tolerance on 10 ordered => max 11. Receiving 12 must be rejected.
        assertThrows(BadRequestException.class,
                () -> service.receiveItems(poId, List.of(Map.of("sku", "SKU-1", "quantityReceived", 12))));
        assertEquals(0, sku1.getQuantityReceived());
    }

    @Test
    void receiveItems_allowsReceiptWithinTolerance() {
        PurchaseOrder po = po();
        PurchaseOrderItem sku1 = item("SKU-1", 10, 0, null);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(sku1));
        when(purchaseOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrder result = service.receiveItems(poId, List.of(Map.of(
                "sku", "SKU-1", "quantityReceived", 11)));

        assertEquals(11, sku1.getQuantityReceived());
        assertEquals(true, result.getIsFullyReceived());
    }

    @Test
    void receiveItems_recommendsPutawayWhenWarehouseProvided() {
        PurchaseOrder po = po();
        UUID warehouseId = UUID.randomUUID();
        PurchaseOrderItem sku1 = item("SKU-1", 10, 0, null);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(sku1));
        when(purchaseOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.receiveItems(poId, List.of(Map.of(
                "sku", "SKU-1", "quantityReceived", 6, "warehouseId", warehouseId.toString())));

        verify(slottingService).recommendPutaway("SKU-1", warehouseId, 6, tenantId.toString());
    }

    @Test
    void receiveItems_invalidWarehouseIdThrowsBadRequest() {
        PurchaseOrder po = po();
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderItemRepository.findByPoId(poId)).thenReturn(List.of(item("SKU-1", 10, 0, null)));

        assertThrows(BadRequestException.class,
                () -> service.receiveItems(poId, List.of(Map.of(
                        "sku", "SKU-1", "quantityReceived", 1, "warehouseId", "not-a-uuid"))));
    }

    @Test
    void approvePurchaseOrder_setsStatusAndAuditFields() {
        PurchaseOrder po = po();
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrder approved = service.approvePurchaseOrder(poId);

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(tenantId, approved.getApprovedBy());
        assertNotNull(approved.getApprovedAt());
    }

    @Test
    void approveRequest_transitionsToApproved() {
        PurchaseRequest req = PurchaseRequest.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .status("PENDING_APPROVAL").build();
        when(purchaseRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(purchaseRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseRequest approved = service.approveRequest(req.getId());

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(tenantId, approved.getApprovedBy());
        assertNotNull(approved.getApprovedAt());
    }
}
