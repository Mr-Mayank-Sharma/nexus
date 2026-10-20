package com.nexus.oms.service;

import com.nexus.oms.dto.AllocationResult;
import com.nexus.oms.entity.NxBrokeringQueue;
import com.nexus.oms.entity.NxBrokeringRun;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderAllocation;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.BrokeringQueueRepository;
import com.nexus.oms.repository.BrokeringRunRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Covers the brokering chain that OrderService.confirmOrder kicks off:
 * enqueue -> scheduler pickup -> allocate. This path had no coverage; the
 * OrderServiceTest/OrderRoutingServiceTest cases exercise confirm and allocate
 * in isolation, so a break between enqueue and the scheduler would pass CI.
 *
 * The behaviours pinned here are the regressions these commits fixed:
 * - double allocation (0b91a0a): enqueue rejects re-queues; the processor skips
 *   orders already allocated manually.
 * - one bad order rolling back the whole run (90bde84): each queue entry runs in
 *   its own transaction, so a failure must not abort the others.
 * - T-17 (allocation correctness): a short-allocated order must not be stamped
 *   green, and re-routing it must not double-reserve the already-covered lines.
 *   Those cases live in the "T-17 regression" section below.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrokeringServiceTest {

    @Mock
    private BrokeringQueueRepository brokeringQueueRepository;
    @Mock
    private BrokeringRunRepository brokeringRunRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderRoutingService orderRoutingService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private BrokeringService brokeringService;

    private UUID tenantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);

        // TransactionTemplate needs a manager that hands back a usable status,
        // otherwise execute() throws before the callback ever runs.
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        // Shared identity/empty stubs. Strictness is LENIENT, so an unused stub is
        // fine, and any per-test re-stub below takes precedence. These let the
        // T-17 enqueue cases and the save-based assertions work without each test
        // re-declaring the same defaults.
        when(brokeringQueueRepository.findByOrderId(any())).thenReturn(new ArrayList<>());
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        brokeringService = new BrokeringService(
                brokeringQueueRepository,
                brokeringRunRepository,
                orderRepository,
                orderRoutingService,
                transactionManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxOrder orderWithStatus(String status) {
        return NxOrder.builder().id(orderId).tenantId(tenantId).status(status).build();
    }

    /**
     * Mockito's List.of() returns an immutable list, and the service sorts the
     * repository result in place (List.sort). JPA never hands back an immutable
     * list, so wrap stubs in a mutable one to mirror reality.
     */
    private static List<NxBrokeringQueue> mutableList(NxBrokeringQueue... entries) {
        return new ArrayList<>(Arrays.asList(entries));
    }

    private NxBrokeringQueue waitingEntry(UUID queueEntryId, UUID entryOrderId, String priority, int attempts) {
        return NxBrokeringQueue.builder()
                .id(queueEntryId)
                .tenantId(tenantId)
                .orderId(entryOrderId)
                .priority(priority)
                .status("WAITING")
                .attempts(attempts)
                .maxAttempts(3)
                .nextRunAt(LocalDateTime.now())
                .enteredAt(LocalDateTime.now())
                .build();
    }

    private NxOrderAllocation allocatedRow() {
        return NxOrderAllocation.builder()
                .nodeId(UUID.randomUUID())
                .nodeName("WH-1")
                .status("ALLOCATED")
                .build();
    }

    // ---------- enqueueOrder ----------

    @Test
    @DisplayName("enqueueOrder for a PENDING order creates a WAITING entry")
    void enqueueOrder_pendingOrder_createsWaitingEntry() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("PENDING")));
        when(brokeringQueueRepository.findByOrderId(orderId)).thenReturn(new ArrayList<>());
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxBrokeringQueue entry = brokeringService.enqueueOrder(orderId, "NORMAL");

        assertEquals("WAITING", entry.getStatus());
        assertEquals(orderId, entry.getOrderId());
        assertEquals(0, entry.getAttempts());
        assertEquals(3, entry.getMaxAttempts());
        verify(brokeringQueueRepository).save(any(NxBrokeringQueue.class));
    }

    @Test
    @DisplayName("enqueueOrder accepts a CONFIRMED order — this is the confirmOrder fallback path")
    void enqueueOrder_confirmedOrder_accepted() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));
        when(brokeringQueueRepository.findByOrderId(orderId)).thenReturn(new ArrayList<>());
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxBrokeringQueue entry = brokeringService.enqueueOrder(orderId, "NORMAL");

        assertEquals("WAITING", entry.getStatus());
    }

    @Test
    @DisplayName("enqueueOrder rejects an order that is neither PENDING nor CONFIRMED")
    void enqueueOrder_wrongStatus_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("ALLOCATED")));

        assertThrows(BadRequestException.class, () -> brokeringService.enqueueOrder(orderId, "NORMAL"));
        verify(brokeringQueueRepository, never()).save(any());
    }

    @Test
    @DisplayName("enqueueOrder throws when the order does not exist")
    void enqueueOrder_unknownOrder_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> brokeringService.enqueueOrder(orderId, "NORMAL"));
    }

    @Test
    @DisplayName("enqueueOrder refuses to double-queue an order that is already WAITING")
    void enqueueOrder_alreadyQueued_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));
        when(brokeringQueueRepository.findByOrderId(orderId)).thenReturn(mutableList(
                NxBrokeringQueue.builder().id(UUID.randomUUID()).status("WAITING").build()));

        assertThrows(BadRequestException.class, () -> brokeringService.enqueueOrder(orderId, "NORMAL"));
        verify(brokeringQueueRepository, never()).save(any());
    }

    // ---------- processQueueEntry via processBrokeringQueue ----------

    @Test
    @DisplayName("scheduler allocates a WAITING order and marks the queue entry ALLOCATED")
    void processBrokeringQueue_waitingOrder_allocates() {
        UUID entryId = UUID.randomUUID();
        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                eq(tenantId), eq("WAITING"), any())).thenReturn(mutableList(waitingEntry(entryId, orderId, "NORMAL", 0)));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID queueOrder = orderId;
        when(brokeringQueueRepository.findById(entryId)).thenReturn(Optional.of(waitingEntry(entryId, queueOrder, "NORMAL", 0)));
        when(orderRepository.findById(queueOrder)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));
        when(orderRoutingService.allocateOrder(any())).thenReturn(AllocationResult.builder()
                .orderId(orderId)
                .status("ALLOCATED")
                .allocations(List.of(allocatedRow()))
                .build());
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        verify(orderRoutingService).allocateOrder(any());
        assertEquals(1, run.getOrdersProcessed());
        assertEquals(1, run.getOrdersAllocated());
        assertEquals(0, run.getOrdersFailed());
        assertEquals("COMPLETED", run.getStatus());
    }

    @Test
    @DisplayName("scheduler skips an order already allocated manually — prevents double allocation")
    void processBrokeringQueue_alreadyAllocatedOrder_skipsAllocation() {
        UUID entryId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry(entryId, orderId, "NORMAL", 0);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                any(), any(), any())).thenReturn(mutableList(entry));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxOrder allocated = orderWithStatus("ALLOCATED");
        allocated.setAllocatedNode(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(allocated));

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        verify(orderRoutingService, never()).allocateOrder(any());
        assertEquals(0, run.getOrdersFailed());
    }

    @Test
    @DisplayName("a shipped order leaves the queue without calling routing, which would refuse and roll back")
    void processBrokeringQueue_shippedOrder_exitsWithoutRouting() {
        UUID entryId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry(entryId, orderId, "NORMAL", 0);
        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(any(), any(), any()))
                .thenReturn(mutableList(entry));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("SHIPPED")));

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        verify(orderRoutingService, never()).allocateOrder(any());
        assertEquals("FAILED", entry.getStatus());
        assertEquals("Order is SHIPPED; nothing left to allocate", entry.getFailureReason());
        assertEquals("COMPLETED", run.getStatus());
    }

    @Test
    @DisplayName("one failing order does not stop the rest of the run — per-order transactions")
    void processBrokeringQueue_oneOrderFails_othersStillProcessed() {
        UUID badEntry = UUID.randomUUID();
        UUID goodEntry = UUID.randomUUID();
        UUID goodOrder = UUID.randomUUID();

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                any(), any(), any()))
                .thenReturn(mutableList(waitingEntry(badEntry, orderId, "NORMAL", 0),
                        waitingEntry(goodEntry, goodOrder, "NORMAL", 0)));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(brokeringQueueRepository.findById(badEntry))
                .thenReturn(Optional.of(waitingEntry(badEntry, orderId, "NORMAL", 0)));
        when(brokeringQueueRepository.findById(goodEntry))
                .thenReturn(Optional.of(waitingEntry(goodEntry, goodOrder, "NORMAL", 0)));
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));
        when(orderRepository.findById(goodOrder)).thenReturn(Optional.of(
                NxOrder.builder().id(goodOrder).tenantId(tenantId).status("CONFIRMED").build()));

        // First order blows up, second allocates.
        when(orderRoutingService.allocateOrder(any()))
                .thenThrow(new RuntimeException("warehouse exploded"))
                .thenReturn(AllocationResult.builder()
                        .orderId(goodOrder)
                        .status("ALLOCATED")
                        .allocations(List.of(allocatedRow()))
                        .build());

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        assertEquals(2, run.getOrdersProcessed());
        assertEquals(1, run.getOrdersAllocated());
        assertEquals(1, run.getOrdersFailed());
        assertEquals("COMPLETED", run.getStatus());
    }

    @Test
    @DisplayName("failed allocation below max attempts re-queues with backoff instead of failing hard")
    void processBrokeringQueue_allocationReturnsPartial_requeues() {
        UUID entryId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry(entryId, orderId, "NORMAL", 0);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                any(), any(), any())).thenReturn(mutableList(entry));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));

        when(orderRoutingService.allocateOrder(any())).thenReturn(AllocationResult.builder()
                .orderId(orderId)
                .status("PARTIALLY_ALLOCATED")
                .allocations(List.of(allocatedRow()))
                .build());

        brokeringService.processBrokeringQueue();

        var saved = org.mockito.ArgumentCaptor.forClass(NxBrokeringQueue.class);
        verify(brokeringQueueRepository, atLeastOnce()).save(saved.capture());
        assertTrue(saved.getAllValues().stream().anyMatch(q -> "WAITING".equals(q.getStatus())),
                "expected the entry to be re-queued as WAITING after a partial allocation");
    }

    @Test
    @DisplayName("allocation that keeps failing until max attempts marks the entry FAILED")
    void processBrokeringQueue_exhaustsAttempts_marksFailed() {
        UUID entryId = UUID.randomUUID();
        // attempts already at maxAttempts-1, so this run exhausts them
        NxBrokeringQueue entry = waitingEntry(entryId, orderId, "NORMAL", 2);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                any(), any(), any())).thenReturn(mutableList(entry));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));

        when(orderRoutingService.allocateOrder(any())).thenReturn(AllocationResult.builder()
                .orderId(orderId)
                .status("FAILED")
                .allocations(List.of())
                .build());

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        assertEquals(1, run.getOrdersFailed());
        var saved = org.mockito.ArgumentCaptor.forClass(NxBrokeringQueue.class);
        verify(brokeringQueueRepository, atLeastOnce()).save(saved.capture());
        assertTrue(saved.getAllValues().stream().anyMatch(q -> "FAILED".equals(q.getStatus())));
    }

    // ---------- processPriorityQueue ----------

    @Test
    @DisplayName("priority processor only picks up HIGH/URGENT entries")
    void processPriorityQueue_picksOnlyHighPriority() {
        UUID entryId = UUID.randomUUID();
        NxBrokeringQueue urgent = waitingEntry(entryId, orderId, "URGENT", 0);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndPriorityIn(
                eq(tenantId), eq("WAITING"), any())).thenReturn(mutableList(urgent));
        when(brokeringRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.findById(entryId)).thenReturn(Optional.of(urgent));
        when(brokeringQueueRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("CONFIRMED")));
        when(orderRoutingService.allocateOrder(any())).thenReturn(AllocationResult.builder()
                .orderId(orderId)
                .status("ALLOCATED")
                .allocations(List.of(allocatedRow()))
                .build());

        NxBrokeringRun run = brokeringService.processPriorityQueue();

        assertEquals("PRIORITY", run.getRunType());
        assertEquals(1, run.getOrdersAllocated());
    }

    // ---------- stats ----------

    @Test
    @DisplayName("queue stats count entries by status")
    void getQueueStats_countsByStatus() {
        when(brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "WAITING")).thenReturn(3L);
        when(brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "FAILED")).thenReturn(1L);

        var stats = brokeringService.getQueueStats(tenantId);

        assertEquals(3L, stats.get("waiting"));
        assertEquals(1L, stats.get("failed"));
    }

    // ---------- T-17 regression (allocation correctness) ----------
    //
    // The cases above prove the queue plumbing. These pin the two ways brokering
    // used to lie about a short-allocated order: refusing to enqueue it, and
    // stamping it green (or re-routing it into a double reservation).

    @Test
    @DisplayName("enqueueOrder accepts a PARTIALLY_ALLOCATED order — it still needs brokering")
    void enqueueOrder_AcceptsPartiallyAllocatedOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("PARTIALLY_ALLOCATED")));

        NxBrokeringQueue entry = brokeringService.enqueueOrder(orderId, "NORMAL");

        assertNotNull(entry);
        assertEquals("WAITING", entry.getStatus());
        assertEquals(orderId, entry.getOrderId());
    }

    @Test
    @DisplayName("enqueueOrder accepts a FAILED order — it still needs brokering")
    void enqueueOrder_AcceptsFailedAllocationOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("FAILED")));

        NxBrokeringQueue entry = brokeringService.enqueueOrder(orderId, "NORMAL");

        assertEquals("WAITING", entry.getStatus());
    }

    @Test
    @DisplayName("enqueueOrder still rejects a SHIPPED order")
    void enqueueOrder_StillRejectsShippedOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("SHIPPED")));

        assertThrows(BadRequestException.class, () -> brokeringService.enqueueOrder(orderId, "NORMAL"));
    }

    @Test
    @DisplayName("a PARTIALLY_ALLOCATED order with an allocated node is FAILED, never re-routed")
    void processQueue_PartiallyAllocatedOrder_MarkedFailedAndNotRerouted() {
        UUID nodeId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry(UUID.randomUUID(), orderId, "NORMAL", 0);
        NxOrder order = orderWithStatus("PARTIALLY_ALLOCATED");
        order.setAllocatedNode(nodeId);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                eq(tenantId), eq("WAITING"), any(LocalDateTime.class))).thenReturn(mutableList(entry));
        when(brokeringQueueRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        assertEquals("FAILED", entry.getStatus());
        assertEquals(1, run.getOrdersFailed());
        assertEquals(0, run.getOrdersAllocated());
        assertNotNull(entry.getFailureReason());
        // The critical assertion: routing only writes item-level allocations for orders that fully
        // land in one node, so re-running it here would reserve the already-covered lines again.
        verify(orderRoutingService, never()).allocateOrder(any());
    }

    @Test
    @DisplayName("a fully ALLOCATED order is marked ALLOCATED without re-routing")
    void processQueue_FullyAllocatedOrder_MarkedAllocatedWithoutRerouting() {
        UUID nodeId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry(UUID.randomUUID(), orderId, "NORMAL", 0);
        NxOrder order = orderWithStatus("ALLOCATED");
        order.setAllocatedNode(nodeId);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                eq(tenantId), eq("WAITING"), any(LocalDateTime.class))).thenReturn(mutableList(entry));
        when(brokeringQueueRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        assertEquals("ALLOCATED", entry.getStatus());
        assertEquals(1, run.getOrdersAllocated());
        verify(orderRoutingService, never()).allocateOrder(any());
    }
}
