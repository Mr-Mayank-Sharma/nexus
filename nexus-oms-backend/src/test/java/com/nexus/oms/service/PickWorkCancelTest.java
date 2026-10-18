package com.nexus.oms.service;

import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** An order has been re-routed to another store: the first store must stop picking it, and be told what to put back. */
class PickWorkCancelTest {

    private final PicklistRepository lists = mock(PicklistRepository.class);
    private final PicklistItemRepository items = mock(PicklistItemRepository.class);
    private final PickingService service = new PickingService(lists, items, mock(WarehouseStaffRepository.class),
            mock(OrderRepository.class), mock(OrderItemRepository.class));
    private final UUID order = UUID.randomUUID(), otherOrder = UUID.randomUUID(), listId = UUID.randomUUID();

    private NxPicklistItem item(UUID orderId, String status) {
        return NxPicklistItem.builder().id(UUID.randomUUID()).picklistId(listId).orderId(orderId).sku("S").quantity(1).status(status).build();
    }

    private NxPicklist list(String status) {
        return NxPicklist.builder().id(listId).status(status).build();
    }

    @Test
    void pendingLinesAreCancelled_andAnEmptiedListIsClosed() {
        var a = item(order, "PENDING");
        var b = item(order, "PENDING");
        when(items.findByOrderId(order)).thenReturn(List.of(a, b));
        when(items.findByPicklistId(listId)).thenReturn(List.of(a, b));
        var pl = list("IN_PROGRESS");
        when(lists.findById(listId)).thenReturn(Optional.of(pl));

        var result = service.cancelPickWorkForOrder(order);

        assertEquals(2, result.tasksCancelled());
        assertEquals(1, result.listsClosed());
        assertEquals("CANCELLED", a.getStatus());
        assertEquals("CANCELLED", pl.getStatus());
    }

    @Test
    void aListThatStillCarriesAnotherOrdersWorkStaysOpen() {
        var mine = item(order, "PENDING");
        var theirs = item(otherOrder, "PENDING");
        when(items.findByOrderId(order)).thenReturn(List.of(mine));
        when(items.findByPicklistId(listId)).thenReturn(List.of(mine, theirs));
        var pl = list("OPEN");
        when(lists.findById(listId)).thenReturn(Optional.of(pl));

        var result = service.cancelPickWorkForOrder(order);

        assertEquals(1, result.tasksCancelled());
        assertEquals(0, result.listsClosed());
        assertEquals("OPEN", pl.getStatus(), "another order's picks must not be cancelled");
        assertEquals("PENDING", theirs.getStatus());
    }

    @Test
    void linesAlreadyPickedAreCountedForTheStoreToPutBack_notSilentlyIgnored() {
        var done = item(order, "PICKED");
        var todo = item(order, "PENDING");
        when(items.findByOrderId(order)).thenReturn(List.of(done, todo));
        when(items.findByPicklistId(listId)).thenReturn(List.of(done, todo));
        var pl = list("IN_PROGRESS");
        when(lists.findById(listId)).thenReturn(Optional.of(pl));

        var result = service.cancelPickWorkForOrder(order);

        assertEquals(1, result.linesAlreadyPicked());
        assertEquals("PICKED", done.getStatus(), "software cannot un-pick; the store is told instead");
        assertEquals("IN_PROGRESS", pl.getStatus(), "a list with picked lines is not closed under the picker");
    }

    @Test
    void anOrderWithNoPickWorkIsANoOp() {
        when(items.findByOrderId(order)).thenReturn(List.of());
        var result = service.cancelPickWorkForOrder(order);
        assertEquals(0, result.tasksCancelled());
        verify(lists, never()).save(any());
    }
}
