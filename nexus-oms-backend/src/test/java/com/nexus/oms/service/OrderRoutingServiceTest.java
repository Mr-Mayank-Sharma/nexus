package com.nexus.oms.service;

import com.nexus.oms.dto.AllocationRequest;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderRoutingServiceTest {

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
    private UUID tenantId;
    private NxOrder order;
    private Warehouse stocked;
    private Warehouse empty;

    @BeforeEach
    void setUp() {
        service = new OrderRoutingService(allocationRepository, exceptionRepository, routingRuleRepository,
                routingConfigRepository, routingLogRepository, warehouseRepository, orderRepository,
                orderItemRepository, inventoryService);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);

        order = NxOrder.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .channel("WEB")
                .status("PENDING")
                .subtotal(new BigDecimal("20.00"))
                .total(new BigDecimal("27.00"))
                .build();

        NxOrderItem item = NxOrderItem.builder()
                .id(UUID.randomUUID())
                .orderId(order.getId())
                .sku("SKU-001")
                .quantity(2)
                .build();
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(item));

        stocked = Warehouse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Seattle DC")
                .status("ACTIVE")
                .type("WAREHOUSE")
                .state("WA")
                .country("US")
                .totalCapacitySqm(new BigDecimal("1000"))
                .usedCapacitySqm(new BigDecimal("100"))
                .build();

        empty = Warehouse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Miami DC")
                .status("ACTIVE")
                .type("WAREHOUSE")
                .state("FL")
                .country("US")
                .totalCapacitySqm(new BigDecimal("1000"))
                .usedCapacitySqm(new BigDecimal("100"))
                .build();

        when(warehouseRepository.findOperational(tenantId))
                .thenReturn(List.of(empty, stocked));
        when(warehouseRepository.findById(stocked.getId())).thenReturn(Optional.of(stocked));
        when(warehouseRepository.findById(empty.getId())).thenReturn(Optional.of(empty));

        // The allocator resolves stock per node; it must never fall back to another node's row.
        stock(empty.getId(), 0, false);
        stock(stocked.getId(), 2, false);

        when(routingConfigRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    }

    /** Stubs a node's available quantity and whether it comes from the shared pool. */
    private void stock(UUID nodeId, int available, boolean shared) {
        when(inventoryService.resolveStock(tenantId, "SKU-001", nodeId))
                .thenReturn(new InventoryService.StockPosition(UUID.randomUUID(), available, shared));
    }

    private AllocationRequest request(String strategy, boolean dryRun) {
        AllocationRequest request = new AllocationRequest();
        request.setOrderId(order.getId());
        request.setStrategy(strategy);
        request.setDryRun(dryRun);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return request;
    }

    private static int allocatedTotal(List<NxOrderAllocation> allocations) {
        return allocations.stream()
                .filter(a -> "ALLOCATED".equals(a.getStatus()))
                .mapToInt(a -> a.getQuantityAllocated() == null ? 0 : a.getQuantityAllocated())
                .sum();
    }

    private static NxOrderAllocation backorder(List<NxOrderAllocation> allocations) {
        return allocations.stream()
                .filter(a -> a.getNodeId() == null && "FAILED".equals(a.getStatus()))
                .findFirst()
                .orElse(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------------------------------------------------------------- baseline behaviour

    @Test
    void ruleBasedAllocation_prefersWarehouseWithStock() {
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of());

        List<NxOrderAllocation> allocations = service.ruleBasedAllocation(order, tenantId);

        assertFalse(allocations.isEmpty());
        assertEquals(stocked.getId(), allocations.get(0).getNodeId());
        assertEquals("ALLOCATED", allocations.get(0).getStatus());
    }

    @Test
    void aiOptimizedAllocation_picksStockedWarehouseOverEmptyOne() {
        List<NxOrderAllocation> allocations = service.aiOptimizedAllocation(order, tenantId);

        assertFalse(allocations.isEmpty());
        assertEquals(stocked.getId(), allocations.get(0).getNodeId());
    }

    @Test
    void allocateOrder_dryRun_doesNotPersistOrChangeStatus() {
        AllocationRequest request = request("AI_OPTIMIZED", true);

        var result = service.allocateOrder(request);

        assertEquals("SIMULATED", result.getStatus());
        assertEquals(stocked.getId(), result.getAllocations().get(0).getNodeId());
        verify(allocationRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(inventoryService, never()).reserveInventory(any(), any(), any(), anyInt(), any(), any());
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    void allocateOrder_throwsWhenNotAllocatable() {
        NxOrder shipped = NxOrder.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status("SHIPPED")
                .build();
        when(orderRepository.findById(shipped.getId())).thenReturn(Optional.of(shipped));

        AllocationRequest request = new AllocationRequest();
        request.setOrderId(shipped.getId());
        request.setStrategy("HYBRID");

        assertThrows(BadRequestException.class, () -> service.allocateOrder(request));
    }

    @Test
    void allocateOrder_persistsAllocationAndSetsOrderState() {
        var result = service.allocateOrder(request("AI_OPTIMIZED", false));

        assertEquals("ALLOCATED", result.getStatus());
        assertEquals("ALLOCATED", order.getStatus());
        assertEquals(stocked.getId(), order.getAllocatedNode());
        verify(inventoryService).reserveInventory(eq(tenantId), eq("SKU-001"), eq(stocked.getId()), eq(2), eq("ORDER"), any());
        verify(allocationRepository, atLeastOnce()).save(any());
        verify(orderRepository, atLeastOnce()).save(order);
    }

    // ------------------------------------------------- T-17: no over-commitment across nodes

    /**
     * T-17 Bug A: a rule naming two nodes used to assign the FULL order quantity to each, so the
     * order was committed twice and blew up at reservation time. Each node may now only take what
     * it can actually source, and the total across nodes may never exceed demand.
     */
    @Test
    void ruleWithTwoNodes_neverCommitsOrderToBothNodes() {
        // Both nodes can cover the whole order on their own.
        stock(empty.getId(), 10, false);
        stock(stocked.getId(), 10, false);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        List<NxOrderAllocation> allocations = service.ruleBasedAllocation(order, tenantId);

        assertEquals(2, allocatedTotal(allocations),
                "sum of allocation quantities must never exceed the 2 units ordered");
    }

    @Test
    void partialStock_splitsAcrossNodesWithoutOvercommitting() {
        // 1 unit at the first node, order needs 2.
        stock(empty.getId(), 1, false);
        stock(stocked.getId(), 5, false);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        var result = service.allocateOrder(request("RULE_BASED", false));

        assertEquals(2, allocatedTotal(result.getAllocations()));
        assertEquals("ALLOCATED", result.getStatus());
        assertNull(backorder(result.getAllocations()), "a fully covered order needs no backorder");
    }

    @Test
    void splitAllocation_doesNotClaimASingleNodeOnTheOrderItems() {
        stock(empty.getId(), 1, false);
        stock(stocked.getId(), 5, false);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        service.allocateOrder(request("RULE_BASED", false));

        NxOrderItem item = orderItemRepository.findByOrderId(order.getId()).get(0);
        assertNull(item.getAllocatedNodeId(),
                "items must not be pinned to one node when the order was split across nodes");
        assertNull(item.getAllocatedQty());
    }

    // ------------------------------------------------------- T-17: explicit, exact backorders

    @Test
    void noStockAnywhere_backordersFullDemandAndFailsTheOrder() {
        stock(empty.getId(), 0, false);
        stock(stocked.getId(), 0, false);

        var result = service.allocateOrder(request("RULE_BASED", false));

        assertEquals(0, allocatedTotal(result.getAllocations()));
        NxOrderAllocation bo = backorder(result.getAllocations());
        assertNotNull(bo, "an order that cannot be sourced must raise a backorder");
        assertEquals(0, bo.getQuantityAllocated());
        assertEquals(2, bo.getQuantityRequested());
        assertEquals("FAILED", result.getStatus());
        assertEquals("FAILED", order.getStatus());
    }

    @Test
    void shortfall_backorderCarriesOnlyTheRemainder() {
        // Only 1 of 2 units available anywhere.
        stock(empty.getId(), 0, false);
        stock(stocked.getId(), 1, false);

        var result = service.allocateOrder(request("RULE_BASED", false));

        assertEquals(1, allocatedTotal(result.getAllocations()));
        NxOrderAllocation bo = backorder(result.getAllocations());
        assertNotNull(bo);
        assertEquals(1, bo.getQuantityRequested(),
                "the backorder must cover the unmet remainder, not the whole order");
        assertEquals("PARTIALLY_ALLOCATED", result.getStatus());
        assertEquals("PARTIALLY_ALLOCATED", order.getStatus());
    }

    /** The shortage must be visible to operators even with automatic detection switched off. */
    @Test
    void shortfall_raisesShortageExceptionEvenWhenDetectionDisabled() {
        stock(empty.getId(), 0, false);
        stock(stocked.getId(), 1, false);
        when(routingConfigRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(NxRoutingConfig.builder()
                        .tenantId(tenantId)
                        .enableExceptionDetection(false)
                        .build()));

        service.allocateOrder(request("RULE_BASED", false));

        ArgumentCaptor<Iterable<NxFulfillmentException>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(exceptionRepository).saveAll(captor.capture());
        assertTrue(StreamSupport.stream(captor.getValue().spliterator(), false)
                        .anyMatch(e -> "INVENTORY_SHORTAGE".equals(e.getExceptionType())),
                "the unmet 1 unit must be raised as an INVENTORY_SHORTAGE exception");
    }

    // --------------------------------------------------------- T-17: shared inventory pools

    /**
     * Two nodes with no inventory row of their own resolve to the SAME tenant+SKU shared row. If
     * each saw the full shared quantity, the allocator would plan to commit it twice.
     */
    @Test
    void sharedPool_isNettedOutSoItCannotBeCommittedTwice() {
        stock(empty.getId(), 2, true);   // shared pool holds exactly the 2 units ordered
        stock(stocked.getId(), 2, true); // same shared row, different node
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        List<NxOrderAllocation> allocations = service.ruleBasedAllocation(order, tenantId);

        assertEquals(2, allocatedTotal(allocations), "shared stock must be committed at most once");
    }

    @Test
    void sharedPool_coversOrderBeforeTouchingASecondNode() {
        stock(empty.getId(), 2, true);
        stock(stocked.getId(), 2, true);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        List<NxOrderAllocation> allocations = service.ruleBasedAllocation(order, tenantId);

        // Demand is satisfied by the first candidate; no second node row should be emitted.
        assertEquals(1, allocations.size());
        assertEquals(empty.getId(), allocations.get(0).getNodeId());
    }

    // ------------------------------------------------------------ reservation safety & tenancy

    @Test
    void reservationFailure_releasesUnitsAlreadyReserved() {
        stock(empty.getId(), 1, false);
        stock(stocked.getId(), 5, false);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        doThrow(new IllegalStateException("inventory row locked"))
                .when(inventoryService).reserveInventory(eq(tenantId), eq("SKU-001"), eq(stocked.getId()), eq(1), eq("ORDER"), any());

        assertThrows(IllegalStateException.class,
                () -> service.allocateOrder(request("RULE_BASED", false)));

        // The first node's unit must be handed back rather than left stranded.
        verify(inventoryService).releaseInventory(eq(tenantId), eq("SKU-001"), eq(empty.getId()), eq(1), eq("ORDER"), any());
    }

    @Test
    void reservations_areTakenAgainstEachAllocatingNode() {
        stock(empty.getId(), 1, false);
        stock(stocked.getId(), 5, false);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(empty.getId(), stocked.getId())));

        service.allocateOrder(request("RULE_BASED", false));

        verify(inventoryService).reserveInventory(eq(tenantId), eq("SKU-001"), eq(empty.getId()), eq(1), eq("ORDER"), any());
        verify(inventoryService).reserveInventory(eq(tenantId), eq("SKU-001"), eq(stocked.getId()), eq(1), eq("ORDER"), any());
    }

    /** A rule is tenant data; it must not be able to route into another tenant's node. */
    @Test
    void ruleCannotAllocateToAnotherTenantsWarehouse() {
        UUID foreignTenant = UUID.randomUUID();
        Warehouse foreign = Warehouse.builder()
                .id(UUID.randomUUID())
                .tenantId(foreignTenant)
                .name("Foreign DC")
                .status("ACTIVE")
                .type("WAREHOUSE")
                .build();
        when(warehouseRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(warehouseRepository.findOperational(tenantId)).thenReturn(List.of(stocked));
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(foreign.getId())));

        List<NxOrderAllocation> allocations = service.ruleBasedAllocation(order, tenantId);

        assertTrue(allocations.stream().noneMatch(a -> foreign.getId().equals(a.getNodeId())),
                "a rule must never allocate to a node outside the tenant");
    }

    // --------------------------------------------------- T-17: hybrid selection prefers coverage

    /**
     * T-17: hybrid selection compared confidence before it compared coverage, so a plan that scored
     * well but could only source part of the order could displace one that covered the order
     * outright. The order would then ship short with nothing explaining the gap. Coverage is the
     * first gate and a shortfall must always surface as an explicit backorder.
     */
    @Test
    void hybrid_prefersThePlanThatCoversTheOrderOverAHigherConfidencePartialPlan() {
        // The AI plan's only node can source half the order, so that plan is necessarily partial.
        Warehouse shortNode = Warehouse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Denver DC")
                .status("ACTIVE")
                .type("WAREHOUSE")
                .state("CO")
                .country("US")
                .totalCapacitySqm(new BigDecimal("1000"))
                .usedCapacitySqm(new BigDecimal("100"))
                .build();
        when(warehouseRepository.findById(shortNode.getId())).thenReturn(Optional.of(shortNode));

        // Demand is 2 units. Spread the stock so the rule's two nodes together cover it, while the
        // AI plan's single node cannot.
        stock(stocked.getId(), 1, false);
        stock(empty.getId(), 1, false);
        stock(shortNode.getId(), 1, false);

        // The rule path reads the active list first and the AI path second, so consecutive returns
        // give the two strategies different candidate sets. The rule's own candidates come from its
        // nodeIds, so the first list only has to be non-empty.
        when(warehouseRepository.findOperational(tenantId))
                .thenReturn(List.of(stocked, empty), List.of(shortNode));
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(stocked.getId(), empty.getId())));

        var result = service.allocateOrder(request("HYBRID", false));

        assertEquals(2, allocatedTotal(result.getAllocations()),
                "hybrid must prefer the plan that covers the whole order over a partial one");
        assertNull(backorder(result.getAllocations()),
                "a fully covered order must not carry a backorder row");
        assertEquals("ALLOCATED", result.getStatus());
        assertEquals("ALLOCATED", order.getStatus());
    }

    // --------------------------------- T-17: promises and confidence must survive a shortfall

    /**
     * T-17: a shortfall is reported as a FAILED backorder row, and the promise calculation
     * returned null whenever <em>any</em> row was FAILED. Because T-17 always emits that row for a
     * partial allocation, every partially-allocated order was published with no delivery date at
     * all -- precisely the orders a promise matters most for. A partial allocation still ships
     * something, so it must still carry a promise.
     */
    @Test
    void partialAllocation_stillReportsAnEstimatedDeliveryDate() {
        stock(empty.getId(), 0, false);
        stock(stocked.getId(), 1, false);

        var result = service.allocateOrder(request("RULE_BASED", false));

        assertEquals(1, allocatedTotal(result.getAllocations()));
        assertNotNull(backorder(result.getAllocations()), "scenario must produce a backorder row");
        assertNotNull(result.getEstimatedDeliveryDate(),
                "a partial allocation must still be promised a delivery date");
        assertNotNull(order.getPromisedDelivery(),
                "the promise must also be persisted onto the order");
    }

    /**
     * Counterpart to the above: the fix must not over-promise. When nothing is being fulfilled
     * there is no node to promise from, so the date stays null and the order fails outright.
     */
    @Test
    void orderThatBackordersInFull_reportsNoEstimatedDeliveryDate() {
        stock(empty.getId(), 0, false);
        stock(stocked.getId(), 0, false);

        var result = service.allocateOrder(request("RULE_BASED", false));

        assertEquals(0, allocatedTotal(result.getAllocations()));
        assertNotNull(backorder(result.getAllocations()));
        assertNull(result.getEstimatedDeliveryDate(),
                "nothing is being fulfilled, so there is nothing to promise");
    }

    /**
     * T-17: the reported plan confidence used to be an average of the per-node confidence scores,
     * with a fixed prior per strategy. Two consequences followed, and both were wrong in the
     * direction that hides risk:
     *
     * <ol>
     *   <li>the same strategy reported the same number regardless of whether it had actually
     *       sourced the order, so a 1-of-2-unit plan scored like a 2-of-2-unit plan; and</li>
     *   <li>averaging node scores into the order score conflated "is this node sound" with "is
     *       this order safe to promise". A short order sourced from two perfect nodes is still a
     *       short order.</li>
     * </ol>
     *
     * <p>The previous test here asserted the plan score must never fall below the weakest node
     * score, which is exactly that conflation stated as a rule. It is replaced rather than
     * relaxed: the invariant is now that coverage moves the score, which is the property the
     * heuristic could not produce.
     */
    @Test
    void planConfidenceRisesWithCoverageAndIsCappedByTheBackorder() {
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(stocked.getId())));

        // --- Fully sourced: 2 of 2 units from one healthy node with headroom. ---
        stock(stocked.getId(), 2, false);
        var full = service.allocateOrder(request("RULE_BASED", true));
        assertEquals(2, allocatedTotal(full.getAllocations()));
        assertNull(backorder(full.getAllocations()), "fully-stocked scenario must not backorder");

        // --- Half sourced: 1 of 2 units, remainder explicitly backordered. ---
        stock(stocked.getId(), 1, false);
        var partial = service.allocateOrder(request("RULE_BASED", true));
        assertEquals(1, allocatedTotal(partial.getAllocations()));
        assertNotNull(backorder(partial.getAllocations()), "scenario must produce a backorder row");

        assertNotNull(full.getConfidenceScore());
        assertNotNull(partial.getConfidenceScore());

        assertTrue(partial.getConfidenceScore().compareTo(full.getConfidenceScore()) < 0,
                "a half-sourced order must score below a fully-sourced one: partial="
                        + partial.getConfidenceScore() + " full=" + full.getConfidenceScore());

        // The node is identical and healthy in both runs, so this difference can only come from
        // coverage. Under the old prior both runs returned 0.7500 and this assertion failed.
        assertTrue(full.getConfidenceScore().compareTo(new BigDecimal("0.4000")) > 0,
                "a fully-sourced order from a healthy node should not be reported as risky: "
                        + full.getConfidenceScore());

        // A backorder is a hard signal that the order is not fully sourced, so it caps the score
        // no matter how healthy the sourcing node is.
        assertTrue(partial.getConfidenceScore().compareTo(new BigDecimal("0.4000")) <= 0,
                "a backordered plan must be capped at 0.4000 regardless of node health: "
                        + partial.getConfidenceScore());
    }

    /**
     * T-17: guards the separation the previous test used to blur. The per-node score answers "is
     * this node sound", the plan score answers "how risky is this order's delivery". They are
     * different measures and must not be reported as the same number.
     */
    @Test
    void nodeConfidenceAndPlanConfidenceAreDistinctMeasures() {
        when(routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId))
                .thenReturn(List.of(ruleWithNodes(stocked.getId())));

        stock(stocked.getId(), 1, false);
        var result = service.allocateOrder(request("RULE_BASED", true));

        BigDecimal nodeScore = result.getAllocations().stream()
                .filter(a -> "ALLOCATED".equals(a.getStatus()))
                .map(NxOrderAllocation::getDeliveryPromiseConfidence)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a fulfilled allocation"));

        assertTrue(nodeScore.compareTo(new BigDecimal("0.4000")) > 0,
                "the sourcing node is ACTIVE with 10% utilisation, so it should sound healthy: "
                        + nodeScore);
        assertTrue(result.getConfidenceScore().compareTo(nodeScore) < 0,
                "a half-sourced plan must report more risk than its healthiest node implies: plan="
                        + result.getConfidenceScore() + " node=" + nodeScore);
    }

    // --------------------------------------------------------------------------- helpers

    private NxRoutingRule ruleWithNodes(UUID... nodeIds) {
        String json = java.util.Arrays.stream(nodeIds)
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{\"nodeIds\":[", "]}"));
        return NxRoutingRule.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("test-rule")
                .priority(1)
                .ruleType("ROUTING")
                .conditions("{}")
                .actions(json)
                .isActive(true)
                .build();
    }
}
