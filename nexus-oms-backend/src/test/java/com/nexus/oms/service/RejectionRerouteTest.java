package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A store rejects part of an order. The first client's stated pain point: do not make a split shipment
 * when another store can ship the whole order in one package, and do not quietly lose the rejected units.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RejectionRerouteTest {

    @Mock private OrderAllocationRepository allocationRepository;
    @Mock private FulfillmentExceptionRepository exceptionRepository;
    @Mock private NxRoutingRuleRepository routingRuleRepository;
    @Mock private RoutingConfigRepository routingConfigRepository;
    @Mock private RoutingLogRepository routingLogRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InventoryService inventoryService;

    private OrderRoutingService service;
    private UUID tenant;
    private NxOrder order;
    private Warehouse storeA;   // rejects
    private Warehouse storeB;   // can ship everything
    private Warehouse storeC;   // cannot
    private NxOrderAllocation allocA;

    @BeforeEach
    void setUp() {
        service = new OrderRoutingService(allocationRepository, exceptionRepository, routingRuleRepository,
                routingConfigRepository, routingLogRepository, warehouseRepository, orderRepository,
                orderItemRepository, inventoryService);
        tenant = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenant);

        order = NxOrder.builder().id(UUID.randomUUID()).tenantId(tenant).channel("WEB").status("ALLOCATED")
                .subtotal(new BigDecimal("20.00")).total(new BigDecimal("27.00")).build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(
                NxOrderItem.builder().id(UUID.randomUUID()).orderId(order.getId()).sku("SKU-001").quantity(2).build()));

        storeA = store("Store A");
        storeB = store("Store B");
        storeC = store("Store C");
        when(warehouseRepository.findOperational(tenant)).thenReturn(List.of(storeA, storeB, storeC));
        for (Warehouse w : List.of(storeA, storeB, storeC)) when(warehouseRepository.findById(w.getId())).thenReturn(Optional.of(w));

        allocA = NxOrderAllocation.builder().id(UUID.randomUUID()).orderId(order.getId()).tenantId(tenant)
                .nodeId(storeA.getId()).nodeName("Store A").status("ALLOCATED").quantityAllocated(2)
                .metadata("{\"skuQuantities\":{\"SKU-001\":2}}").build();
        when(allocationRepository.findByOrderId(order.getId())).thenReturn(List.of(allocA));
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(routingConfigRepository.findByTenantId(tenant)).thenReturn(Optional.empty());

        availability(storeA, 0);
        availability(storeB, 2);
        availability(storeC, 1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Warehouse store(String name) {
        return Warehouse.builder().id(UUID.randomUUID()).tenantId(tenant).name(name).status("ACTIVE").type("STORE")
                .state("NY").country("US").totalCapacitySqm(new BigDecimal("1000")).usedCapacitySqm(new BigDecimal("100")).build();
    }

    private void availability(Warehouse w, int available) {
        when(inventoryService.checkAvailability(eq(tenant), eq("SKU-001"), eq(w.getId()), anyInt()))
                .thenAnswer(inv -> (int) inv.getArgument(3) <= available);
        when(inventoryService.resolveStock(tenant, "SKU-001", w.getId()))
                .thenReturn(new InventoryService.StockPosition(UUID.randomUUID(), available, false));
    }

    @Test
    void whenAnotherStoreHoldsTheWholeOrder_itIsSentThereAsOnePackage() {
        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(OrderRoutingService.RerouteResult.REROUTED_WHOLE_ORDER, result.outcome());
        assertEquals(storeB.getId(), result.toNodeId());
        // The rejecting store's reservation is given back, in full, against the order.
        verify(inventoryService).releaseInventory(tenant, "SKU-001", storeA.getId(), 2, "ORDER", order.getId().toString());
        assertEquals("CANCELLED", allocA.getStatus());
        // And the whole quantity is reserved at the new store.
        verify(inventoryService).reserveInventory(tenant, "SKU-001", storeB.getId(), 2, "ORDER", order.getId().toString());
        verify(inventoryService, never()).reserveInventory(eq(tenant), anyString(), eq(storeC.getId()), anyInt(), any(), any());
        assertEquals("ALLOCATED", order.getStatus());
        assertEquals(storeB.getId(), order.getAllocatedNode());
    }

    @Test
    void aStoreThatAlreadyRejectedTheOrderIsNeverChosenAgain() {
        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of(storeB.getId()));

        // B was the only node that could ship everything and it is excluded, so no whole-order home exists.
        assertEquals(OrderRoutingService.RerouteResult.SHORTFALL_RAISED, result.outcome());
        verify(inventoryService, never()).reserveInventory(eq(tenant), anyString(), eq(storeB.getId()), anyInt(), any(), any());
    }

    @Test
    void whenNobodyHoldsTheWholeOrder_theStoreKeepsItsLinesAndTheShortfallIsRaised() {
        availability(storeB, 1);   // only half the order anywhere else

        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(OrderRoutingService.RerouteResult.SHORTFALL_RAISED, result.outcome());
        assertNull(result.toNodeId());
        // Only the rejected unit is given back; the store keeps shipping the other one.
        verify(inventoryService).releaseInventory(tenant, "SKU-001", storeA.getId(), 1, "ORDER", order.getId().toString());
        assertEquals("ALLOCATED", allocA.getStatus());
        assertEquals(1, allocA.getQuantityAllocated());
        assertTrue(allocA.getMetadata().contains("\"SKU-001\":1"), allocA.getMetadata());
        assertEquals("PARTIALLY_ALLOCATED", order.getStatus());
        ArgumentCaptor<NxFulfillmentException> raised = ArgumentCaptor.forClass(NxFulfillmentException.class);
        verify(exceptionRepository).save(raised.capture());
        assertEquals("INVENTORY_SHORTAGE", raised.getValue().getType());
        assertEquals("OPEN", raised.getValue().getStatus());
    }

    @Test
    void anOrderThatIsNoLongerAllocatedIsLeftAlone() {
        order.setStatus("SHIPPED");

        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(OrderRoutingService.RerouteResult.NOT_APPLICABLE, result.outcome());
        verifyNoInteractions(inventoryService);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void reallocatingAnAllocatedOrder_givesBackTheOldReservation_beforeAllocatingAgain() {
        // Used to throw ("must be PENDING or CONFIRMED") and, had it run, left the old stock reserved.
        availability(storeA, 0);
        availability(storeB, 2);

        service.reallocateOrder(order.getId(), "AI_OPTIMIZED");

        verify(inventoryService).releaseInventory(tenant, "SKU-001", storeA.getId(), 2, "ORDER", order.getId().toString());
        assertEquals("CANCELLED", allocA.getStatus());
        verify(inventoryService).reserveInventory(eq(tenant), eq("SKU-001"), eq(storeB.getId()), eq(2), eq("ORDER"), eq(order.getId().toString()));
        assertEquals("ALLOCATED", order.getStatus());
    }

    @Test
    void reallocatingAnOrderThatIsAlreadyBeingPacked_isRefusedWithAClearReason() {
        order.setStatus("PACKING");

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.reallocateOrder(order.getId(), "AI_OPTIMIZED"));
        assertTrue(e.getMessage().contains("record a rejection"), e.getMessage());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void aWholeOrderReroute_cancelsTheOldStoresPickWork_andTellsThemWhatToPutBack() {
        PickingService picking = mock(PickingService.class);
        when(picking.cancelPickWorkForOrder(order.getId())).thenReturn(new PickingService.PickWorkCancelled(2, 1, 1));
        service.setPickingService(picking);

        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(OrderRoutingService.RerouteResult.REROUTED_WHOLE_ORDER, result.outcome());
        verify(picking).cancelPickWorkForOrder(order.getId());
        assertTrue(result.note().contains("2 open pick task(s) cancelled"), result.note());
        assertTrue(result.note().contains("1 line(s) were already picked and must be put back"), result.note());
    }

    @Test
    void whenNobodyCanTakeTheWholeOrder_theStoresPickWorkIsLeftAlone() {
        PickingService picking = mock(PickingService.class);
        service.setPickingService(picking);
        availability(storeB, 1);

        service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        verifyNoInteractions(picking);
    }

    // ---- failure-aware routing ---------------------------------------------------------------------------

    private PickFailureService ratesWhere(UUID badStore, double rate) {
        PickFailureService rates = mock(PickFailureService.class);
        when(rates.ratesFor(tenant)).thenReturn(java.util.Map.of(
                badStore, new PickFailureService.NodeRate(badStore, 100, Math.round(rate * 100), rate)));
        return rates;
    }

    @Test
    void withNoFailureHistory_twoIdenticalStoresTie_andTheFirstIsChosen() {
        availability(storeC, 2);   // B and C can both ship the whole order

        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(storeB.getId(), result.toNodeId());
    }

    @Test
    void aStoreThatOftenFailsPicks_isNotChosenWhenAnEquallyGoodStoreIsAvailable_onReroute() {
        availability(storeC, 2);
        service.setPickFailureService(ratesWhere(storeB.getId(), 0.40));

        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(storeC.getId(), result.toNodeId());
        verify(inventoryService).reserveInventory(tenant, "SKU-001", storeC.getId(), 2, "ORDER", order.getId().toString());
    }

    @Test
    void aStoreThatOftenFailsPicks_isRankedLower_whenAnOrderIsFirstAllocated() {
        availability(storeC, 2);
        order.setStatus("CONFIRMED");
        when(allocationRepository.findByOrderId(order.getId())).thenReturn(List.of());
        service.setPickFailureService(ratesWhere(storeB.getId(), 0.40));
        var request = new com.nexus.oms.dto.AllocationRequest();
        request.setOrderId(order.getId());
        request.setStrategy("AI_OPTIMIZED");
        request.setDryRun(false);

        service.allocateOrder(request);

        verify(inventoryService).reserveInventory(eq(tenant), eq("SKU-001"), eq(storeC.getId()), eq(2), eq("ORDER"), eq(order.getId().toString()));
        verify(inventoryService, never()).reserveInventory(eq(tenant), anyString(), eq(storeB.getId()), anyInt(), any(), any());
    }

    @Test
    void failureRatesNeverMakeAnUnstockedStoreWin() {
        // C fails far less, but cannot ship the order; B can. Stock still decides who is eligible.
        availability(storeC, 0);
        service.setPickFailureService(ratesWhere(storeB.getId(), 0.40));

        var result = service.rerouteAfterRejection(order.getId(), storeA.getId(), "SKU-001", 1, Set.of());

        assertEquals(storeB.getId(), result.toNodeId());
    }

    // ---- the rejection flow around it -------------------------------------------------------------------

    private static final class Flow {
        final OrderRejectionRepository rejections = mock(OrderRejectionRepository.class);
        final RejectionReasonRepository reasons = mock(RejectionReasonRepository.class);
        final OrderRoutingService routing = mock(OrderRoutingService.class);
        final InventoryService inventory = mock(InventoryService.class);
        final OrderAllocationRepository allocations = mock(OrderAllocationRepository.class);
        final OrderRepository orders = mock(OrderRepository.class);
        final RejectionService service = new RejectionService(reasons, rejections, routing, inventory, allocations, orders);
    }

    private NxRejectionReason reason(String code, String impact) {
        return NxRejectionReason.builder().id(UUID.randomUUID()).code(code).inventoryImpact(impact)
                .requiresPhoto(false).requiresNotes(false).active(true).build();
    }

    private void orderKnown(Flow f) {
        when(f.orders.findById(order.getId())).thenReturn(Optional.of(order));
    }

    private NxOrderRejection rejection(UUID reasonId, int qty) {
        return NxOrderRejection.builder().orderId(order.getId()).orderNumber("ORD-1").sku("SKU-001")
                .rejectionReasonId(reasonId).quantity(qty).rejectedBy("picker").nodeId(storeA.getId())
                .status("PENDING").inventoryAdjusted(false).build();
    }

    @Test
    void rejectingAPick_reroutes_thenHoldsTheUnitsOutOfAvailability() {
        Flow f = new Flow();
        orderKnown(f);
        NxRejectionReason notFound = reason("NOT_FOUND", "QUARANTINE");
        when(f.reasons.findById(notFound.getId())).thenReturn(Optional.of(notFound));
        when(f.rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(f.rejections.findByOrderId(order.getId())).thenReturn(List.of());
        when(f.routing.rerouteAfterRejection(any(), any(), any(), anyInt(), any())).thenReturn(
                new OrderRoutingService.RerouteResult(OrderRoutingService.RerouteResult.REROUTED_WHOLE_ORDER, storeA.getId(), storeB.getId(), "sent to B"));
        when(f.inventory.holdUnits(tenant, "SKU-001", storeA.getId(), 1)).thenReturn(1);

        NxOrderRejection saved = f.service.rejectItem(rejection(notFound.getId(), 1));

        InOrder order = inOrder(f.routing, f.inventory);
        order.verify(f.routing).rerouteAfterRejection(any(), eq(storeA.getId()), eq("SKU-001"), eq(1), any());
        order.verify(f.inventory).holdUnits(tenant, "SKU-001", storeA.getId(), 1);
        assertEquals("REROUTED_WHOLE_ORDER", saved.getRerouteOutcome());
        assertEquals("PENDING", saved.getStatus());
        assertFalse(saved.getInventoryAdjusted(), "nothing has been written off yet");
    }

    @Test
    void aRestockReason_doesNotHideAnyStock() {
        Flow f = new Flow();
        orderKnown(f);
        NxRejectionReason fine = reason("CUSTOMER_CANCELLED", "RESTOCK");
        when(f.reasons.findById(fine.getId())).thenReturn(Optional.of(fine));
        when(f.rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(f.rejections.findByOrderId(order.getId())).thenReturn(List.of());
        when(f.routing.rerouteAfterRejection(any(), any(), any(), anyInt(), any())).thenReturn(
                new OrderRoutingService.RerouteResult(OrderRoutingService.RerouteResult.NOT_APPLICABLE, storeA.getId(), null, "n/a"));

        f.service.rejectItem(rejection(fine.getId(), 1));

        verify(f.inventory, never()).holdUnits(any(), any(), any(), anyInt());
    }

    @Test
    void storesThatAlreadyRejectedTheOrderAreExcludedFromTheNextRoute() {
        Flow f = new Flow();
        orderKnown(f);
        NxRejectionReason notFound = reason("NOT_FOUND", "QUARANTINE");
        UUID earlierStore = UUID.randomUUID();
        when(f.reasons.findById(notFound.getId())).thenReturn(Optional.of(notFound));
        when(f.rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(f.rejections.findByOrderId(order.getId())).thenReturn(List.of(
                NxOrderRejection.builder().nodeId(earlierStore).build()));
        when(f.routing.rerouteAfterRejection(any(), any(), any(), anyInt(), any())).thenReturn(
                new OrderRoutingService.RerouteResult(OrderRoutingService.RerouteResult.SHORTFALL_RAISED, storeA.getId(), null, "x"));

        f.service.rejectItem(rejection(notFound.getId(), 1));

        verify(f.routing).rerouteAfterRejection(any(), any(), any(), anyInt(), argThat(set -> set.contains(earlierStore)));
    }

    @Test
    void ifTheStoreIsNotNamedAndTwoStoresHoldTheSku_itAsksWhichOne() {
        Flow f = new Flow();
        orderKnown(f);
        NxRejectionReason notFound = reason("NOT_FOUND", "QUARANTINE");
        when(f.reasons.findById(notFound.getId())).thenReturn(Optional.of(notFound));
        NxOrderAllocation b = NxOrderAllocation.builder().nodeId(storeB.getId()).status("ALLOCATED")
                .nodeName("Store B").metadata("{\"skuQuantities\":{\"SKU-001\":1}}").build();
        when(f.allocations.findByOrderId(order.getId())).thenReturn(List.of(allocA, b));
        NxOrderRejection unnamed = rejection(notFound.getId(), 1);
        unnamed.setNodeId(null);

        BadRequestException e = assertThrows(BadRequestException.class, () -> f.service.rejectItem(unnamed));
        assertTrue(e.getMessage().contains("which store"), e.getMessage());
        verifyNoInteractions(f.routing);
    }

    @Test
    void approvingAWriteOff_reallyTakesTheUnitsOffOnHand() {
        Flow f = new Flow();
        NxOrderRejection r = rejection(UUID.randomUUID(), 2);
        r.setId(UUID.randomUUID());
        r.setTenantId(tenant);
        r.setInventoryAction("DAMAGE_WRITE_OFF");
        r.setRejectionCode("DAMAGED");
        when(f.rejections.findById(r.getId())).thenReturn(Optional.of(r));
        when(f.rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(f.inventory.writeOffHeldUnits(eq(tenant), eq("SKU-001"), eq(storeA.getId()), eq(2), eq("REJECTION"), eq(r.getId().toString()), anyString())).thenReturn(2);

        NxOrderRejection done = f.service.processRejection(r.getId());

        assertEquals("PROCESSED", done.getStatus());
        assertTrue(done.getInventoryAdjusted());
    }

    @Test
    void approvingAQuarantine_changesNoOnHand_andDoesNotClaimItDid() {
        Flow f = new Flow();
        NxOrderRejection r = rejection(UUID.randomUUID(), 1);
        r.setId(UUID.randomUUID());
        r.setTenantId(tenant);
        r.setInventoryAction("QUARANTINE");
        when(f.rejections.findById(r.getId())).thenReturn(Optional.of(r));
        when(f.rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxOrderRejection done = f.service.processRejection(r.getId());

        assertEquals("PROCESSED", done.getStatus());
        assertFalse(done.getInventoryAdjusted(), "it used to be set to true with nothing having moved");
        verify(f.inventory, never()).writeOffHeldUnits(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void anotherTenantsOrderCannotBeRejectedAgainst() {
        Flow f = new Flow();
        NxRejectionReason notFound = reason("NOT_FOUND", "QUARANTINE");
        when(f.reasons.findById(notFound.getId())).thenReturn(Optional.of(notFound));
        NxOrder foreign = NxOrder.builder().id(order.getId()).tenantId(UUID.randomUUID()).status("ALLOCATED").build();
        when(f.orders.findById(order.getId())).thenReturn(Optional.of(foreign));

        assertThrows(com.nexus.oms.exception.ResourceNotFoundException.class,
                () -> f.service.rejectItem(rejection(notFound.getId(), 1)));
        verifyNoInteractions(f.routing, f.inventory);
    }

    @Test
    void theOrderNumberIsTakenFromTheOrder_notFromTheCaller() {
        Flow f = new Flow();
        orderKnown(f);
        order.setChannelOrderId("1009");
        NxRejectionReason fine = reason("CUSTOMER_CANCELLED", "RESTOCK");
        when(f.reasons.findById(fine.getId())).thenReturn(Optional.of(fine));
        when(f.rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(f.rejections.findByOrderId(order.getId())).thenReturn(List.of());
        when(f.routing.rerouteAfterRejection(any(), any(), any(), anyInt(), any())).thenReturn(
                new OrderRoutingService.RerouteResult(OrderRoutingService.RerouteResult.NOT_APPLICABLE, storeA.getId(), null, "n/a"));
        NxOrderRejection r = rejection(fine.getId(), 1);
        r.setOrderNumber(null);

        assertEquals("1009", f.service.rejectItem(r).getOrderNumber());
    }
}
