package com.nexus.oms.service;

import com.nexus.oms.entity.NxTransferOrder;
import com.nexus.oms.entity.NxTransferOrderItem;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.TransferOrderItemRepository;
import com.nexus.oms.repository.TransferOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Shipping and receiving a transfer must actually move stock between locations. */
class TransferOrderServiceTest {

    private final TransferOrderRepository orders = mock(TransferOrderRepository.class);
    private final TransferOrderItemRepository items = mock(TransferOrderItemRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();
    private TransferOrderService service;
    private NxTransferOrder transfer;
    private NxTransferOrderItem line;

    @BeforeEach
    void setUp() {
        service = new TransferOrderService(orders, items, inventory);
        transfer = new NxTransferOrder();
        transfer.setId(UUID.randomUUID());
        transfer.setTenantId(tenant);
        transfer.setSourceNodeId(source);
        transfer.setDestinationNodeId(destination);
        transfer.setStatus("APPROVED");
        line = new NxTransferOrderItem();
        line.setId(UUID.randomUUID());
        line.setSku("A");
        line.setQuantityRequested(10);
        line.setStatus("PENDING");
        when(orders.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(items.findByTransferOrderId(transfer.getId())).thenReturn(List.of(line));
    }

    @Test
    void shipping_takesTheRequestedQuantityOutOfTheSource() {
        NxTransferOrder result = service.shipTransferOrder(transfer.getId(), UUID.randomUUID(), null);

        assertEquals("IN_TRANSIT", result.getStatus());
        verify(inventory).transferOut(tenant, "A", source, destination, 10, transfer.getId().toString());
    }

    @Test
    void shipping_aSpecifiedQuantity_movesThatQuantity_andNullMeansAsRequested() {
        NxTransferOrderItem partial = new NxTransferOrderItem();
        partial.setSku("A");
        partial.setQuantityShipped(4);

        service.shipTransferOrder(transfer.getId(), UUID.randomUUID(), List.of(partial));
        verify(inventory).transferOut(tenant, "A", source, destination, 4, transfer.getId().toString());

        transfer.setStatus("APPROVED");
        line.setStatus("PENDING");
        NxTransferOrderItem unspecified = new NxTransferOrderItem();
        unspecified.setSku("A");
        service.shipTransferOrder(transfer.getId(), UUID.randomUUID(), List.of(unspecified));
        verify(inventory).transferOut(tenant, "A", source, destination, 10, transfer.getId().toString());
    }

    @Test
    void shipping_whenTheSourceCannotCoverIt_failsAndTheTransferStaysApproved() {
        doThrow(new BadRequestException("Only 3 of A can be moved")).when(inventory)
                .transferOut(any(), any(), any(), any(), anyInt(), any());

        assertThrows(BadRequestException.class, () -> service.shipTransferOrder(transfer.getId(), UUID.randomUUID(), null));

        assertEquals("APPROVED", transfer.getStatus());
        verify(orders, never()).save(argThat(t -> "IN_TRANSIT".equals(t.getStatus())));
    }

    @Test
    void shipping_withoutBothLocations_isRefused() {
        transfer.setDestinationNodeId(null);

        assertThrows(BadRequestException.class, () -> service.shipTransferOrder(transfer.getId(), UUID.randomUUID(), null));

        verifyNoInteractions(inventory);
    }

    @Test
    void receiving_addsTheReceivedQuantityAtTheDestination() {
        transfer.setStatus("IN_TRANSIT");
        line.setQuantityShipped(10);
        line.setStatus("SHIPPED");

        NxTransferOrder result = service.receiveTransferOrder(transfer.getId(), UUID.randomUUID(), null);

        assertEquals("RECEIVED", result.getStatus());
        verify(inventory).transferIn(tenant, "A", destination, 10, 10, transfer.getId().toString());
    }

    @Test
    void receivingLessThanWasShipped_recordsTheShortfall() {
        transfer.setStatus("IN_TRANSIT");
        line.setQuantityShipped(10);
        line.setStatus("SHIPPED");
        NxTransferOrderItem got = new NxTransferOrderItem();
        got.setSku("A");
        got.setQuantityReceived(8);

        service.receiveTransferOrder(transfer.getId(), UUID.randomUUID(), List.of(got));

        verify(inventory).transferIn(eq(tenant), eq("A"), eq(destination), eq(8), eq(10), any());
    }

    @Test
    void cannotShipOrReceiveOutOfSequence() {
        transfer.setStatus("DRAFT");
        assertThrows(BadRequestException.class, () -> service.shipTransferOrder(transfer.getId(), UUID.randomUUID(), null));
        assertThrows(BadRequestException.class, () -> service.receiveTransferOrder(transfer.getId(), UUID.randomUUID(), null));
        verifyNoInteractions(inventory);
    }
}
