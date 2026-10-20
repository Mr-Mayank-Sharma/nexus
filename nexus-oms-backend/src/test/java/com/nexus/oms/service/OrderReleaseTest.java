package com.nexus.oms.service;

import com.nexus.oms.dto.AllocationRequest;
import com.nexus.oms.dto.AllocationResult;
import com.nexus.oms.entity.NxBrokeringQueue;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxRoutingConfig;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Release by ship date: an order with a far-off requested ship date (typically B2B) is held until ship date minus a
 * window; while held it is never allocated (so it reserves nothing) and brokering waits instead of failing it.
 */
class OrderReleaseTest {

    final UUID tenant = UUID.randomUUID();
    OrderRepository orders = mock(OrderRepository.class);
    RoutingConfigRepository configs = mock(RoutingConfigRepository.class);
    BrokeringQueueRepository queue = mock(BrokeringQueueRepository.class);
    BrokeringService brokering = mock(BrokeringService.class);
    FulfilmentReleaseService service = new FulfilmentReleaseService(orders, configs, queue, brokering);

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(tenant);
        when(orders.save(any())).thenAnswer(a -> a.getArgument(0));
        when(queue.findByOrderId(any())).thenReturn(new ArrayList<>());
        when(configs.findByTenantId(tenant)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    NxOrder order(String type, LocalDateTime shipDate, String status) {
        NxOrder o = NxOrder.builder().id(UUID.randomUUID()).tenantId(tenant).status(status).orderType(type).requestedShipDate(shipDate).build();
        when(orders.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void aB2bOrderShippingInTwoMonthsIsHeldUntilThreeDaysBefore() {
        LocalDateTime ship = LocalDateTime.now().plusDays(60);
        NxOrder o = order("b2b", ship, "PENDING");
        service.plan(o);
        assertEquals("B2B", o.getOrderType());
        assertEquals(ship.minusDays(3), o.getReleaseAt());
        assertEquals(FulfilmentReleaseService.AWAITING_RELEASE, o.getSubStatus());
        assertTrue(FulfilmentReleaseService.isHeld(o, LocalDateTime.now()));
        assertFalse(FulfilmentReleaseService.isHeld(o, ship.minusDays(2)), "inside the window it is no longer held");
    }

    @Test
    void ordersWithoutAShipDateAreNeverHeld() {
        NxOrder o = order(null, null, "PENDING");
        service.plan(o);
        assertEquals("B2C", o.getOrderType());
        assertNull(o.getReleaseAt());
        assertFalse(FulfilmentReleaseService.isHeld(o, LocalDateTime.now()));
    }

    @Test
    void aB2cOrderShippingTomorrowIsNotHeldButOneNextMonthIs() {
        NxOrder soon = order("B2C", LocalDateTime.now().plusHours(20), "PENDING");
        service.plan(soon);
        assertFalse(FulfilmentReleaseService.isHeld(soon, LocalDateTime.now()), "1-day B2C window: shipping tomorrow releases now");
        NxOrder later = order("B2C", LocalDateTime.now().plusDays(30), "PENDING");
        service.plan(later);
        assertTrue(FulfilmentReleaseService.isHeld(later, LocalDateTime.now()), "a scheduled delivery next month waits");
    }

    @Test
    void theWindowsComeFromTheTenantsRoutingSettings() {
        NxRoutingConfig c = NxRoutingConfig.builder().tenantId(tenant).releaseDaysB2b(10).releaseDaysB2c(0).build();
        when(configs.findByTenantId(tenant)).thenReturn(Optional.of(c));
        LocalDateTime ship = LocalDateTime.now().plusDays(30);
        NxOrder o = order("B2B", ship, "PENDING");
        service.plan(o);
        assertEquals(ship.minusDays(10), o.getReleaseAt());
        assertEquals(new FulfilmentReleaseService.Policy(10, 0), service.policy(tenant));
    }

    @Test
    void changingAWindowRePlansTheOrdersOnHold() {
        NxRoutingConfig c = NxRoutingConfig.builder().tenantId(tenant).releaseDaysB2b(3).releaseDaysB2c(1).build();
        when(configs.findByTenantId(tenant)).thenReturn(Optional.of(c));
        LocalDateTime ship = LocalDateTime.now().plusDays(40);
        NxOrder held = order("B2B", ship, "CONFIRMED");
        service.plan(held);
        when(orders.findByTenantIdAndReleasedAtIsNullAndReleaseAtIsNotNullOrderByReleaseAtAsc(tenant)).thenReturn(List.of(held));
        service.updatePolicy(tenant, 7, 1);
        assertEquals(ship.minusDays(7), held.getReleaseAt());
        assertEquals(7, c.getReleaseDaysB2b());
        assertThrows(BadRequestException.class, () -> service.updatePolicy(tenant, -1, 1));
        assertThrows(BadRequestException.class, () -> service.updatePolicy(tenant, 3, 999));
    }

    @Test
    void onlyOrdersNotYetAllocatedCanBeRescheduled() {
        NxOrder allocated = order("B2B", null, "ALLOCATED");
        assertThrows(BadRequestException.class, () -> service.schedule(tenant, allocated.getId(), "B2B", LocalDateTime.now().plusDays(20)));
        NxOrder open = order("B2C", null, "PENDING");
        NxOrder saved = service.schedule(tenant, open.getId(), "b2b", LocalDateTime.now().plusDays(20));
        assertEquals("B2B", saved.getOrderType());
        assertTrue(FulfilmentReleaseService.isHeld(saved, LocalDateTime.now()));
        assertThrows(BadRequestException.class, () -> service.schedule(tenant, open.getId(), "WHOLESALE", null));
    }

    @Test
    void clearingTheShipDateOfAHeldOrderReleasesItNow() {
        NxOrder o = order("B2B", LocalDateTime.now().plusDays(30), "PENDING");
        service.plan(o);
        service.schedule(tenant, o.getId(), "B2B", null);
        assertNotNull(o.getReleasedAt());
        verify(brokering).enqueueOrder(o.getId(), "HIGH");
    }

    @Test
    void anotherTenantsOrderIsNotFound() {
        NxOrder o = order("B2B", LocalDateTime.now().plusDays(20), "PENDING");
        o.setTenantId(UUID.randomUUID());
        assertThrows(com.nexus.oms.exception.ResourceNotFoundException.class, () -> service.releaseNow(tenant, o.getId()));
    }

    @Test
    void releasingByHandEndsTheHoldAndQueuesTheOrder() {
        NxOrder o = order("B2B", LocalDateTime.now().plusDays(20), "PENDING");
        service.plan(o);
        service.releaseNow(tenant, o.getId());
        assertNotNull(o.getReleasedAt());
        assertNull(o.getSubStatus());
        assertFalse(FulfilmentReleaseService.isHeld(o, LocalDateTime.now()));
        verify(brokering).enqueueOrder(o.getId(), "HIGH");
    }

    @Test
    void aQueueEntryParkedUntilReleaseRunsNowInsteadOfQueueingTwice() {
        NxOrder o = order("B2B", LocalDateTime.now().plusDays(20), "CONFIRMED");
        service.plan(o);
        NxBrokeringQueue waiting = NxBrokeringQueue.builder().id(UUID.randomUUID()).orderId(o.getId()).status("WAITING").nextRunAt(o.getReleaseAt()).build();
        when(queue.findByOrderId(o.getId())).thenReturn(List.of(waiting));
        service.releaseNow(tenant, o.getId());
        assertFalse(waiting.getNextRunAt().isAfter(LocalDateTime.now()));
        verify(queue).save(waiting);
        verify(brokering, never()).enqueueOrder(any(), any());
    }

    @Test
    void theSchedulerReleasesWhatIsDue() {
        NxOrder due = order("B2B", LocalDateTime.now().plusDays(1), "CONFIRMED");
        due.setReleaseAt(LocalDateTime.now().minusMinutes(5));
        when(orders.findTop500ByReleasedAtIsNullAndReleaseAtLessThanEqualAndStatusInOrderByReleaseAtAsc(any(), eq(List.of("PENDING", "CONFIRMED")))).thenReturn(List.of(due));
        TenantContext.clear();
        assertEquals(1, service.releaseDue(LocalDateTime.now()));
        assertNotNull(due.getReleasedAt());
        verify(brokering).enqueueOrder(due.getId(), "HIGH");
    }

    // ---------------------------------------------------------------- the guards in allocation and brokering

    @Test
    void allocationRefusesAHeldOrderAndReservesNothing() {
        OrderAllocationRepository allocations = mock(OrderAllocationRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        OrderRoutingService routing = new OrderRoutingService(allocations, mock(FulfillmentExceptionRepository.class), mock(NxRoutingRuleRepository.class),
                configs, mock(RoutingLogRepository.class), mock(WarehouseRepository.class), orders, mock(OrderItemRepository.class), inventory);
        NxOrder o = order("B2B", LocalDateTime.now().plusDays(45), "CONFIRMED");
        service.plan(o);
        AllocationRequest r = new AllocationRequest();
        r.setOrderId(o.getId());
        r.setDryRun(false);
        AllocationResult result = routing.allocateOrder(r);
        assertEquals("HELD", result.getStatus());
        assertTrue(result.getExplanation().contains("no stock is reserved"));
        assertTrue(result.getAllocations().isEmpty());
        assertEquals(FulfilmentReleaseService.AWAITING_RELEASE, o.getSubStatus());
        verifyNoInteractions(inventory, allocations);
    }

    @Test
    void brokeringWaitsForTheReleaseTimeWithoutSpendingAnAttempt() {
        BrokeringRunRepository runs = mock(BrokeringRunRepository.class);
        OrderRoutingService routing = mock(OrderRoutingService.class);
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(runs.save(any())).thenAnswer(a -> a.getArgument(0));
        when(queue.save(any())).thenAnswer(a -> a.getArgument(0));
        BrokeringService real = new BrokeringService(queue, runs, orders, routing, txm);

        NxOrder o = order("B2B", LocalDateTime.now().plusDays(45), "CONFIRMED");
        service.plan(o);
        NxBrokeringQueue entry = real.enqueueOrder(o.getId(), "NORMAL");
        assertEquals(o.getReleaseAt(), entry.getNextRunAt(), "a held order is queued for its release time, not in 5 minutes");

        entry.setId(UUID.randomUUID());
        entry.setNextRunAt(LocalDateTime.now().minusMinutes(1));      // pretend it came due early
        when(queue.findByTenantIdAndStatusAndNextRunAtBefore(eq(tenant), eq("WAITING"), any())).thenReturn(new ArrayList<>(List.of(entry)));
        when(queue.findById(entry.getId())).thenReturn(Optional.of(entry));
        real.processBrokeringQueue();
        assertEquals("WAITING", entry.getStatus());
        assertEquals(0, entry.getAttempts());
        assertEquals(o.getReleaseAt(), entry.getNextRunAt());
        verify(routing, never()).allocateOrder(any());
    }
}
