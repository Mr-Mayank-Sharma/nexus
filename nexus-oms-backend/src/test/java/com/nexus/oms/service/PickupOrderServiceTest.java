package com.nexus.oms.service;

import com.nexus.oms.entity.NxPickupOrder;
import com.nexus.oms.entity.NxPickupOrderItem;
import com.nexus.oms.entity.NxProofOfDelivery;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.PickupOrderItemRepository;
import com.nexus.oms.repository.PickupOrderRepository;
import com.nexus.oms.repository.ProofOfDeliveryRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickupOrderServiceTest {

    @Mock private PickupOrderRepository pickupOrderRepository;
    @Mock private PickupOrderItemRepository pickupOrderItemRepository;
    @Mock private ProofOfDeliveryRepository proofOfDeliveryRepository;

    private PickupOrderService service;
    private UUID tenantId;
    private UUID pickupId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        service = new PickupOrderService(pickupOrderRepository, pickupOrderItemRepository, proofOfDeliveryRepository);
        tenantId = UUID.randomUUID();
        pickupId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxPickupOrder pickup(String status) {
        return NxPickupOrder.builder().id(pickupId).tenantId(tenantId)
                .orderNumber("BP-100").pickupType("BOPIS").status(status).build();
    }

    private NxPickupOrderItem item(int qty, int picked) {
        return NxPickupOrderItem.builder().id(itemId).pickupOrderId(pickupId)
                .sku("SKU-1").productName("Widget").quantity(qty)
                .pickedQuantity(picked).status("PENDING").build();
    }

    @Test
    void assignPicker_transitionsPendingToPicking() {
        NxPickupOrder order = pickup("PENDING");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));
        when(pickupOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID pickerId = UUID.randomUUID();

        NxPickupOrder result = service.assignPicker(pickupId, pickerId, "Ravi");

        assertEquals("PICKING", result.getStatus());
        assertEquals(pickerId, result.getPickerId());
        assertEquals("Ravi", result.getPickerName());
    }

    @Test
    void assignPicker_rejectsNonPendingOrder() {
        NxPickupOrder order = pickup("PICKED");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> service.assignPicker(pickupId, UUID.randomUUID(), "Ravi"));
    }

    @Test
    void pickItem_fullPickMarksPicked() {
        NxPickupOrderItem orderItem = item(5, 0);
        when(pickupOrderItemRepository.findById(itemId)).thenReturn(Optional.of(orderItem));
        when(pickupOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxPickupOrderItem result = service.pickItem(itemId, 5, null);

        assertEquals(5, result.getPickedQuantity());
        assertEquals("PICKED", result.getStatus());
    }

    @Test
    void pickItem_partialPickMarksShort() {
        NxPickupOrderItem orderItem = item(5, 0);
        when(pickupOrderItemRepository.findById(itemId)).thenReturn(Optional.of(orderItem));
        when(pickupOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxPickupOrderItem result = service.pickItem(itemId, 3, "out of stock");

        assertEquals("SHORT", result.getStatus());
        assertEquals("out of stock", result.getNotes());
    }

    @Test
    void pickItem_overQuantityThrows() {
        NxPickupOrderItem orderItem = item(5, 0);
        when(pickupOrderItemRepository.findById(itemId)).thenReturn(Optional.of(orderItem));

        assertThrows(BadRequestException.class, () -> service.pickItem(itemId, 6, null));
        verify(pickupOrderItemRepository, never()).save(any());
    }

    @Test
    void completePicking_requiresAllItemsPicked() {
        NxPickupOrder order = pickup("PICKING");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));
        when(pickupOrderItemRepository.findByPickupOrderId(pickupId)).thenReturn(List.of(item(5, 0)));

        assertThrows(BadRequestException.class, () -> service.completePicking(pickupId));
    }

    @Test
    void completePicking_marksPickedWhenAllDone() {
        NxPickupOrder order = pickup("PICKING");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));
        when(pickupOrderItemRepository.findByPickupOrderId(pickupId)).thenReturn(List.of(item(5, 5)));
        when(pickupOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxPickupOrder result = service.completePicking(pickupId);

        assertEquals("PICKED", result.getStatus());
        assertNotNull(result.getPickedAt());
    }

    @Test
    void packAndHandoff_followLifecycle() {
        NxPickupOrder order = pickup("PICKED");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));
        when(pickupOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxPickupOrder packed = service.packOrder(pickupId);
        assertEquals("PACKED", packed.getStatus());

        NxPickupOrder ready = service.markReadyForHandoff(pickupId);
        assertEquals("READY_FOR_HANDOFF", ready.getStatus());

        NxPickupOrder handed = service.handoffOrder(pickupId);
        assertEquals("HANDED_OFF", handed.getStatus());
        assertNotNull(handed.getHandedOffAt());
    }

    @Test
    void packOrder_rejectsNotPicked() {
        NxPickupOrder order = pickup("PENDING");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> service.packOrder(pickupId));
    }

    @Test
    void collectOrder_savesPODAndMarksCollected() {
        NxPickupOrder order = pickup("HANDED_OFF");
        when(pickupOrderRepository.findById(pickupId)).thenReturn(Optional.of(order));
        when(proofOfDeliveryRepository.save(any())).thenAnswer(inv -> {
            NxProofOfDelivery pod = inv.getArgument(0);
            pod.setId(UUID.randomUUID());
            return pod;
        });
        when(pickupOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxProofOfDelivery pod = NxProofOfDelivery.builder().collectedByName("Alex")
                .collectorSignature("sig").itemsHandedOver(5).build();
        NxProofOfDelivery saved = service.collectOrder(pickupId, pod);

        assertEquals(pickupId, saved.getPickupOrderId());
        assertEquals("BP-100", saved.getOrderNumber());
        assertEquals(tenantId, saved.getTenantId());
        assertNotNull(saved.getCollectedAt());
        assertEquals("POD_COLLECTED", order.getStatus());
    }

    @Test
    void substituteItem_marksSubstituted() {
        NxPickupOrderItem orderItem = item(5, 0);
        when(pickupOrderItemRepository.findById(itemId)).thenReturn(Optional.of(orderItem));
        when(pickupOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxPickupOrderItem result = service.substituteItem(itemId, "SKU-2", 5);

        assertEquals("SKU-2", result.getSubstitutedSku());
        assertEquals("SUBSTITUTED", result.getStatus());
    }

    @Test
    void markNoShows_flagsExpiredReadyOrdersOnly() {
        UUID nodeId = UUID.randomUUID();
        NxPickupOrder expired = NxPickupOrder.builder().id(UUID.randomUUID())
                .tenantId(tenantId).status("READY_FOR_HANDOFF")
                .readyAt(LocalDateTime.now().minusHours(72)).build();
        NxPickupOrder fresh = NxPickupOrder.builder().id(UUID.randomUUID())
                .tenantId(tenantId).status("READY_FOR_HANDOFF")
                .readyAt(LocalDateTime.now().minusHours(2)).build();
        when(pickupOrderRepository.findByNodeIdAndStatus(nodeId, "READY_FOR_HANDOFF"))
                .thenReturn(List.of(expired, fresh));
        when(pickupOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NxPickupOrder> noShows = service.markNoShows(nodeId, Duration.ofHours(48));

        assertEquals(1, noShows.size());
        assertEquals("NO_SHOW", noShows.get(0).getStatus());
        verify(pickupOrderRepository, times(1)).save(any());
    }

    @Test
    void getPickupKPIs_computesRateAndWaitTimes() {
        UUID nodeId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        NxPickupOrder collected = NxPickupOrder.builder().tenantId(tenantId)
                .status("POD_COLLECTED")
                .estimatedReadyAt(now.minusHours(3))
                .readyAt(now.minusHours(3))
                .collectedAt(now.minusHours(1))
                .createdAt(now.minusHours(5))
                .build();
        NxPickupOrder noShow = NxPickupOrder.builder().tenantId(tenantId)
                .status("NO_SHOW").build();
        when(pickupOrderRepository.findByNodeIdAndStatus(nodeId, null))
                .thenReturn(List.of(collected, noShow));

        Map<String, Object> kpis = service.getPickupKPIs(nodeId);

        assertEquals(2L, kpis.get("totalPickups"));
        assertEquals(1L, kpis.get("collected"));
        assertEquals(1L, kpis.get("noShows"));
        assertEquals(0.5, kpis.get("noShowRate"));
        assertEquals(0.5, kpis.get("onTimeReadyRate"));
        assertEquals(120.0, kpis.get("avgWaitMinutes"));
        assertEquals(240.0, kpis.get("avgCycleMinutes"));
    }
}
