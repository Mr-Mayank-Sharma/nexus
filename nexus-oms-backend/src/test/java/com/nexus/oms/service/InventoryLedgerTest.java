package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxInventoryMovement;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.InventoryMovementRepository;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.service.bigcommerce.BigCommerceInventorySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** The inventory ledger and the rule that shipping takes stock off the books. */
class InventoryLedgerTest {

    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID node = UUID.randomUUID();
    private final UUID otherNode = UUID.randomUUID();
    private InventoryService service;
    private OrderStockCommit commit;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventory, mock(BigCommerceInventorySyncService.class), movements,
                new InventoryLedger(movements, inventory), mock(PlatformTransactionManager.class));
        commit = new OrderStockCommit(orderItems, service);
        when(inventory.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private NxInventory row(UUID nodeId, int onHand, int allocated) {
        return NxInventory.builder().id(UUID.randomUUID()).tenantId(tenant).sku("A").nodeId(nodeId)
                .quantityOnHand(onHand).quantityAllocated(allocated).quantityReserved(0).safetyStock(0).build();
    }

    private NxInventoryMovement lastMovement() {
        ArgumentCaptor<NxInventoryMovement> c = ArgumentCaptor.forClass(NxInventoryMovement.class);
        verify(movements, atLeastOnce()).save(c.capture());
        return c.getValue();
    }

    @Test
    void shipping_takesUnitsOffOnHandAndOffTheAllocation_andWritesALedgerLine() {
        NxInventory r = row(node, 50, 3);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.findById(r.getId())).thenReturn(Optional.of(r));

        int taken = service.consumeAllocated(tenant, "A", node, 3, "ORDER", "o-1");

        assertEquals(3, taken);
        assertEquals(47, r.getQuantityOnHand());
        assertEquals(0, r.getQuantityAllocated());
        NxInventoryMovement m = lastMovement();
        assertEquals(NxInventoryMovement.SHIPMENT, m.getMovementType());
        assertEquals(-3, m.getOnHandDelta());
        assertEquals(-3, m.getAllocatedDelta());
        assertEquals("ORDER", m.getReferenceType());
        assertEquals("o-1", m.getReferenceId());
        assertNull(m.getReason());
    }

    @Test
    void shipping_moreThanIsOnHand_clampsAtZero_andSaysSoInTheLedger() {
        NxInventory r = row(node, 2, 5);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.findById(r.getId())).thenReturn(Optional.of(r));

        int taken = service.consumeAllocated(tenant, "A", node, 5, "ORDER", "o-2");

        assertEquals(2, taken);
        assertEquals(0, r.getQuantityOnHand(), "never negative");
        assertEquals(0, r.getQuantityAllocated());
        assertTrue(lastMovement().getReason().contains("only 2 were on hand"));
    }

    @Test
    void anAdjustmentOfASkuHeldAtTwoLocations_mustSayWhichOne() {
        when(inventory.findByTenantIdAndSku(tenant, "A")).thenReturn(List.of(row(node, 10, 0), row(otherNode, 10, 0)));

        BadRequestException e = assertThrows(BadRequestException.class, () -> service.adjustInventoryBySku(tenant, "A", null, 5, "recount"));

        assertTrue(e.getMessage().contains("2 locations"));
        verify(inventory, never()).save(any());
        verifyNoInteractions(movements);
    }

    @Test
    void anAdjustmentWithALocation_changesThatRowOnly_andIsLedgered() {
        NxInventory here = row(node, 10, 0);
        NxInventory there = row(otherNode, 10, 0);
        when(inventory.findByTenantIdAndSku(tenant, "A")).thenReturn(List.of(here, there));

        service.adjustInventoryBySku(tenant, "A", otherNode, -4, "damaged in transit");

        assertEquals(10, here.getQuantityOnHand());
        assertEquals(6, there.getQuantityOnHand());
        NxInventoryMovement m = lastMovement();
        assertEquals(NxInventoryMovement.ADJUSTMENT, m.getMovementType());
        assertEquals(-4, m.getOnHandDelta());
        assertEquals(otherNode, m.getNodeId());
        assertEquals("damaged in transit", m.getReason());
    }

    @Test
    void reducingBelowZero_isRefused() {
        when(inventory.findByTenantIdAndSku(tenant, "A")).thenReturn(List.of(row(node, 3, 0)));

        assertThrows(BadRequestException.class, () -> service.adjustInventoryBySku(tenant, "A", null, -4, null));
    }

    @Test
    void reserving_writesAReserveLine() {
        NxInventory r = row(node, 20, 0);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.reserveAtomicByRow(r.getId(), 4)).thenReturn(1);
        when(inventory.findById(r.getId())).thenReturn(Optional.of(r));

        service.reserveInventory(tenant, "A", node, 4, "ORDER", "o-3");

        NxInventoryMovement m = lastMovement();
        assertEquals(NxInventoryMovement.RESERVE, m.getMovementType());
        assertEquals(4, m.getAllocatedDelta());
        assertEquals(0, m.getOnHandDelta());
    }

    @Test
    void aFailedReservation_writesNoLedgerLine() {
        NxInventory r = row(node, 1, 0);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.reserveAtomicByRow(r.getId(), 5)).thenReturn(0);

        assertThrows(BadRequestException.class, () -> service.reserveInventory(tenant, "A", node, 5, "ORDER", "o-4"));

        verifyNoInteractions(movements);
    }

    @Test
    void releasing_recordsOnlyWhatWasActuallyReleased() {
        NxInventory r = row(node, 20, 2);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.findById(r.getId())).thenReturn(Optional.of(r));

        service.releaseInventory(tenant, "A", node, 5, "ORDER", "o-5");   // asked for 5, only 2 were allocated

        assertEquals(-2, lastMovement().getAllocatedDelta());
    }

    @Test
    void aTransfer_movesStockOutOfTheSource_holdsItInTransit_andAddsItOnArrival() {
        NxInventory source = row(node, 20, 5);
        NxInventory dest = row(otherNode, 1, 0);
        dest.setQuantityInTransit(0);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(source));
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", otherNode)).thenReturn(Optional.of(dest));

        service.transferOut(tenant, "A", node, otherNode, 10, "t-1");

        assertEquals(10, source.getQuantityOnHand());
        assertEquals(10, dest.getQuantityInTransit());
        assertEquals(1, dest.getQuantityOnHand(), "not yet arrived");
        assertEquals(NxInventoryMovement.TRANSFER_OUT, lastMovement().getMovementType());
        assertEquals(-10, lastMovement().getOnHandDelta());

        service.transferIn(tenant, "A", otherNode, 8, 10, "t-1");

        assertEquals(9, dest.getQuantityOnHand());
        assertEquals(0, dest.getQuantityInTransit());
        NxInventoryMovement in = lastMovement();
        assertEquals(NxInventoryMovement.TRANSFER_IN, in.getMovementType());
        assertEquals(8, in.getOnHandDelta());
        assertTrue(in.getReason().contains("received 8 of 10"));
    }

    @Test
    void aTransfer_cannotTakeStockThatIsAllocatedToOrders() {
        NxInventory source = row(node, 20, 15);   // only 5 are free
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(source));

        BadRequestException e = assertThrows(BadRequestException.class, () -> service.transferOut(tenant, "A", node, otherNode, 6, "t-2"));

        assertTrue(e.getMessage().contains("Only 5"));
        assertEquals(20, source.getQuantityOnHand());
        verifyNoInteractions(movements);
    }

    @Test
    void aTransfer_createsTheDestinationRowWhenItHasNone() {
        NxInventory source = row(node, 20, 0);
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(source));
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", otherNode)).thenReturn(Optional.empty());

        service.transferOut(tenant, "A", node, otherNode, 5, "t-3");

        verify(inventory, atLeastOnce()).save(argThat(r -> otherNode.equals(r.getNodeId()) && r.getQuantityInTransit() != null && r.getQuantityInTransit() == 5));
    }

    @Test
    void aReturn_isRestockedAtTheLocationTheOrderShippedFrom() {
        NxInventory origin = row(node, 10, 0);
        NxInventory elsewhere = row(otherNode, 99, 0);
        when(inventory.findByTenantIdAndSku(tenant, "A")).thenReturn(List.of(origin, elsewhere));

        service.restockReturn(tenant, "A", node, 2, "r-1");

        assertEquals(12, origin.getQuantityOnHand());
        assertEquals(99, elsewhere.getQuantityOnHand());
        NxInventoryMovement m = lastMovement();
        assertEquals(NxInventoryMovement.RETURN, m.getMovementType());
        assertEquals("r-1", m.getReferenceId());
    }

    @Test
    void aReturn_withNoKnownOrigin_goesToTheOnlyOrTheBiggestLocation_neverRefused() {
        NxInventory small = row(node, 3, 0);
        NxInventory big = row(otherNode, 40, 0);
        when(inventory.findByTenantIdAndSku(tenant, "A")).thenReturn(List.of(small, big));

        service.restockReturn(tenant, "A", null, 1, "r-2");

        assertEquals(41, big.getQuantityOnHand());
        assertEquals(3, small.getQuantityOnHand());
    }

    @Test
    void aReturn_ofASkuWithNoStockAnywhere_createsTheFirstRow() {
        when(inventory.findByTenantIdAndSku(tenant, "A")).thenReturn(List.of());
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.empty());

        service.restockReturn(tenant, "A", node, 4, "r-3");

        verify(inventory, atLeastOnce()).save(argThat(r -> node.equals(r.getNodeId()) && r.getQuantityOnHand() != null && r.getQuantityOnHand() == 4));
    }

    // ---- OrderStockCommit ---------------------------------------------------------------------

    private NxOrderItem item(int qty, int allocated, int consumed, UUID nodeId) {
        return NxOrderItem.builder().id(UUID.randomUUID()).sku("A").quantity(qty).allocatedQty(allocated)
                .consumedQty(consumed).allocatedNodeId(nodeId).build();
    }

    private NxOrder order() {
        NxOrder o = new NxOrder();
        o.setId(UUID.randomUUID());
        o.setTenantId(tenant);
        return o;
    }

    @Test
    void shippingAnOrder_consumesEachAllocatedLineOnce() {
        NxOrder o = order();
        NxInventory r = row(node, 50, 3);
        NxOrderItem line = item(3, 3, 0, node);
        when(orderItems.findByOrderId(o.getId())).thenReturn(List.of(line));
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.findById(r.getId())).thenReturn(Optional.of(r));

        assertEquals(3, commit.consumeOnShip(o));
        assertEquals(3, line.getConsumedQty());
        assertEquals(47, r.getQuantityOnHand());

        // shipped again (packing, then a manual status change): nothing more comes off
        assertEquals(0, commit.consumeOnShip(o));
        assertEquals(47, r.getQuantityOnHand());
        verify(movements, times(1)).save(any());
    }

    @Test
    void aLineWithNothingAllocated_isLeftAlone() {
        NxOrder o = order();
        when(orderItems.findByOrderId(o.getId())).thenReturn(List.of(item(3, 0, 0, null)));

        assertEquals(0, commit.consumeOnShip(o));
        verifyNoInteractions(movements);
        verify(inventory, never()).save(any());
    }

    @Test
    void aLineAllocatedWithoutANode_isSkippedNotGuessed() {
        NxOrder o = order();
        when(orderItems.findByOrderId(o.getId())).thenReturn(List.of(item(3, 3, 0, null)));

        assertEquals(0, commit.consumeOnShip(o));
        verify(inventory, never()).save(any());
    }

    @Test
    void aPartiallyConsumedLine_takesOnlyTheRemainder() {
        NxOrder o = order();
        NxInventory r = row(node, 50, 2);
        NxOrderItem line = item(5, 5, 3, node);
        when(orderItems.findByOrderId(o.getId())).thenReturn(List.of(line));
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", node)).thenReturn(Optional.of(r));
        when(inventory.findById(r.getId())).thenReturn(Optional.of(r));

        assertEquals(2, commit.consumeOnShip(o));
        assertEquals(5, line.getConsumedQty());
        verify(inventory, atLeastOnce()).save(any());
        verify(orderItems).save(line);
        verify(movements, times(1)).save(any());   // exactly one SHIPMENT line
    }

    @Test
    void zeroOrNegativeQuantity_isIgnored() {
        assertEquals(0, service.consumeAllocated(tenant, "A", node, 0, "ORDER", "x"));
        verify(inventory, never()).findById(any());
        verify(orderItems, never()).save(any());
        verify(inventory, never()).reserveAtomicByRow(any(), anyInt());
    }
}
