package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaveServiceTest {

    @Mock private WaveRepository waveRepository;
    @Mock private WaveRuleRepository waveRuleRepository;
    @Mock private PicklistRepository picklistRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PicklistItemRepository picklistItemRepository;

    private WaveService waveService;
    private UUID tenantId;
    private UUID waveId;

    @BeforeEach
    void setUp() {
        waveService = new WaveService(waveRepository, waveRuleRepository, picklistRepository,
                warehouseRepository, orderRepository, orderItemRepository, picklistItemRepository);
        tenantId = UUID.randomUUID();
        waveId = UUID.randomUUID();
    }

    private NxWave draftWave(String waveType) {
        return NxWave.builder().id(waveId).tenantId(tenantId).name("Wave-1")
                .warehouseId(UUID.randomUUID()).status("DRAFT").priority("HIGH").waveType(waveType).build();
    }

    private NxOrder order(UUID id, String status, String channel) {
        return NxOrder.builder().id(id).tenantId(tenantId).status(status).channel(channel)
                .shipFrom("ZONE-A").paymentStatus("PAID").createdAt(LocalDateTime.now()).build();
    }

    @Test
    void planWave_matchesOnlyPickableOrdersAndExcludesAlreadyPicklisted() {
        NxWave wave = draftWave("SINGLE");
        when(waveRepository.findById(waveId)).thenReturn(Optional.of(wave));
        when(waveRuleRepository.findByWaveIdAndIsActive(waveId, true)).thenReturn(List.of());

        NxOrder pickable = order(UUID.randomUUID(), "APPROVED", "SHOPIFY");
        NxOrder notPickable = order(UUID.randomUUID(), "SHIPPED", "SHOPIFY");
        when(orderRepository.findByTenantId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pickable, notPickable)));

        NxPicklist active = NxPicklist.builder().id(UUID.randomUUID()).status("OPEN")
                .orderIds(pickable.getId().toString()).build();
        when(picklistRepository.findByTenantId(tenantId)).thenReturn(List.of(active));

        when(waveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxWave planned = waveService.planWave(waveId);

        assertEquals("PLANNED", planned.getStatus());
        assertEquals(0, planned.getOrderCount());
        assertEquals(0, planned.getTotalLineItems());
    }

    @Test
    void planWave_appliesChannelRule() {
        NxWave wave = draftWave("SINGLE");
        when(waveRepository.findById(waveId)).thenReturn(Optional.of(wave));
        NxWaveRule rule = NxWaveRule.builder().ruleType("CHANNEL").operator("=").value("AMAZON").isActive(true).build();
        when(waveRuleRepository.findByWaveIdAndIsActive(waveId, true)).thenReturn(List.of(rule));

        NxOrder shopify = order(UUID.randomUUID(), "APPROVED", "SHOPIFY");
        NxOrder amazon = order(UUID.randomUUID(), "APPROVED", "AMAZON");
        when(orderRepository.findByTenantId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(shopify, amazon)));
        when(picklistRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(waveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxWave planned = waveService.planWave(waveId);

        assertEquals(1, planned.getOrderCount());
    }

    @Test
    void releaseWave_createsRealPicklistAndItemsFromRealOrders() {
        NxWave wave = draftWave("SINGLE");
        wave.setStatus("PLANNED");
        when(waveRepository.findById(waveId)).thenReturn(Optional.of(wave));
        when(waveRuleRepository.findByWaveIdAndIsActive(waveId, true)).thenReturn(List.of());

        NxOrder pickable = order(UUID.randomUUID(), "APPROVED", "SHOPIFY");
        when(orderRepository.findByTenantId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pickable)));
        when(picklistRepository.findByTenantId(tenantId)).thenReturn(List.of());

        NxOrderItem item = NxOrderItem.builder().id(UUID.randomUUID()).orderId(pickable.getId())
                .sku("SKU-1").productName("Widget").quantity(2).build();
        when(orderItemRepository.findByOrderId(pickable.getId())).thenReturn(List.of(item));

        NxPicklist savedPicklist = NxPicklist.builder().id(UUID.randomUUID()).status("OPEN").build();
        when(picklistRepository.save(any())).thenAnswer(inv -> {
            NxPicklist p = inv.getArgument(0);
            p.setId(savedPicklist.getId());
            return p;
        });
        when(waveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxWave released = waveService.releaseWave(waveId, "test-user");

        assertEquals("IN_PROGRESS", released.getStatus());
        assertEquals(1, released.getOrderCount());
        assertEquals(2, released.getReleasedLineItems());

        ArgumentCaptor<NxPicklist> picklistCaptor = ArgumentCaptor.forClass(NxPicklist.class);
        verify(picklistRepository).save(picklistCaptor.capture());
        assertEquals("OPEN", picklistCaptor.getValue().getStatus());
        assertTrue(picklistCaptor.getValue().getOrderIds().contains(pickable.getId().toString()));

        ArgumentCaptor<NxPicklistItem> itemCaptor = ArgumentCaptor.forClass(NxPicklistItem.class);
        verify(picklistItemRepository).save(itemCaptor.capture());
        assertEquals("SKU-1", itemCaptor.getValue().getSku());
        assertEquals(2, itemCaptor.getValue().getQuantity());

        assertEquals("ALLOCATED", pickable.getStatus());
        verify(orderRepository).save(pickable);
    }
}
