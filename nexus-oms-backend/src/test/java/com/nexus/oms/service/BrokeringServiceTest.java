package com.nexus.oms.service;

import com.nexus.oms.entity.NxBrokeringQueue;
import com.nexus.oms.entity.NxBrokeringRun;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.BrokeringQueueRepository;
import com.nexus.oms.repository.BrokeringRunRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Brokering is the retry path for orders that routing could not fully cover, so these tests pin
 * down the two ways it used to lie: refusing to enqueue a short-allocated order, and stamping a
 * short-allocated order green (or worse, re-routing it into a double reservation).
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

    private BrokeringService brokeringService;
    private UUID tenantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        brokeringService = new BrokeringService(brokeringQueueRepository, brokeringRunRepository,
                orderRepository, orderRoutingService, transactionManager);
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(brokeringRunRepository.save(any(NxBrokeringRun.class))).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.save(any(NxBrokeringQueue.class))).thenAnswer(i -> i.getArgument(0));
        when(brokeringQueueRepository.findByOrderId(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void enqueueOrder_AcceptsPartiallyAllocatedOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("PARTIALLY_ALLOCATED")));

        NxBrokeringQueue entry = brokeringService.enqueueOrder(orderId, "NORMAL");

        assertNotNull(entry);
        assertEquals("WAITING", entry.getStatus());
        assertEquals(orderId, entry.getOrderId());
    }

    @Test
    void enqueueOrder_AcceptsFailedAllocationOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("FAILED")));

        NxBrokeringQueue entry = brokeringService.enqueueOrder(orderId, "NORMAL");

        assertEquals("WAITING", entry.getStatus());
    }

    @Test
    void enqueueOrder_StillRejectsShippedOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderWithStatus("SHIPPED")));

        assertThrows(BadRequestException.class, () -> brokeringService.enqueueOrder(orderId, "NORMAL"));
    }

    @Test
    void processQueue_PartiallyAllocatedOrder_MarkedFailedAndNotRerouted() {
        UUID nodeId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry();
        NxOrder order = orderWithStatus("PARTIALLY_ALLOCATED");
        order.setAllocatedNode(nodeId);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                eq(tenantId), eq("WAITING"), any(LocalDateTime.class))).thenReturn(mutableListOf(entry));
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
    void processQueue_FullyAllocatedOrder_MarkedAllocatedWithoutRerouting() {
        UUID nodeId = UUID.randomUUID();
        NxBrokeringQueue entry = waitingEntry();
        NxOrder order = orderWithStatus("ALLOCATED");
        order.setAllocatedNode(nodeId);

        when(brokeringQueueRepository.findByTenantIdAndStatusAndNextRunAtBefore(
                eq(tenantId), eq("WAITING"), any(LocalDateTime.class))).thenReturn(mutableListOf(entry));
        when(brokeringQueueRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        NxBrokeringRun run = brokeringService.processBrokeringQueue();

        assertEquals("ALLOCATED", entry.getStatus());
        assertEquals(1, run.getOrdersAllocated());
        verify(orderRoutingService, never()).allocateOrder(any());
    }

    /**
     * JPA hands back a mutable list and BrokeringService sorts it in place by priority, so the
     * stub has to be mutable too — List.of() would throw UnsupportedOperationException and mask
     * the behaviour under test.
     */
    private List<NxBrokeringQueue> mutableListOf(NxBrokeringQueue entry) {
        List<NxBrokeringQueue> list = new java.util.ArrayList<>();
        list.add(entry);
        return list;
    }

    private NxBrokeringQueue waitingEntry() {
        return NxBrokeringQueue.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .orderId(orderId)
                .priority("NORMAL")
                .status("WAITING")
                .attempts(0)
                .maxAttempts(3)
                .nextRunAt(LocalDateTime.now().minusMinutes(1))
                .enteredAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    private NxOrder orderWithStatus(String status) {
        return NxOrder.builder()
                .id(orderId)
                .tenantId(tenantId)
                .status(status)
                .build();
    }
}
