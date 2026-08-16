package com.nexus.oms.service;

import com.nexus.oms.entity.NxEndlessAisleOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.EndlessAisleOrderRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndlessAisleServiceTest {

    @Mock private EndlessAisleOrderRepository endlessAisleOrderRepository;
    @Mock private InventoryService inventoryService;

    private EndlessAisleService service;
    private UUID tenantId;
    private UUID orderId;
    private UUID storeId;
    private NxEndlessAisleOrder order;

    @BeforeEach
    void setUp() {
        service = new EndlessAisleService(endlessAisleOrderRepository, inventoryService);
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        order = NxEndlessAisleOrder.builder()
                .id(orderId)
                .tenantId(tenantId)
                .storeId(storeId)
                .productSku("SKU-100")
                .quantity(5)
                .unitPrice(BigDecimal.TEN)
                .totalAmount(BigDecimal.valueOf(50))
                .fulfillmentType("SHIP_TO_CUSTOMER")
                .status("PROCESSING")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createOrder_setsTenantStatusAndTotal() {
        TenantContext.setCurrentTenantId(tenantId);
        NxEndlessAisleOrder input = NxEndlessAisleOrder.builder()
                .storeId(storeId)
                .productSku("SKU-1")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(12.50))
                .build();
        when(endlessAisleOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxEndlessAisleOrder saved = service.createOrder(input);

        assertEquals(tenantId, saved.getTenantId());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(BigDecimal.valueOf(25.00), saved.getTotalAmount());
    }

    @Test
    void createOrder_negativeQuantityThrows() {
        TenantContext.setCurrentTenantId(tenantId);
        NxEndlessAisleOrder input = NxEndlessAisleOrder.builder()
                .productSku("SKU-1").quantity(0).unitPrice(BigDecimal.ONE).build();

        assertThrows(BadRequestException.class, () -> service.createOrder(input));
    }

    @Test
    void updateStatus_processing_deductsStoreInventory() {
        order.setStatus("CONFIRMED");
        when(endlessAisleOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(endlessAisleOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxEndlessAisleOrder result = service.updateStatus(orderId, "PROCESSING", null);

        assertEquals("PROCESSING", result.getStatus());
        verify(inventoryService).adjustInventoryBySkuAtNode(tenantId, "SKU-100", storeId, -5);
    }

    @Test
    void updateStatus_cancelled_restoresStoreInventory() {
        order.setStatus("CONFIRMED");
        when(endlessAisleOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(endlessAisleOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxEndlessAisleOrder result = service.updateStatus(orderId, "CANCELLED", "out of stock");

        assertEquals("CANCELLED", result.getStatus());
        verify(inventoryService).adjustInventoryBySkuAtNode(tenantId, "SKU-100", storeId, 5);
    }

    @Test
    void updateStatus_invalidTransitionThrows() {
        order.setStatus("DELIVERED");
        when(endlessAisleOrderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> service.updateStatus(orderId, "PROCESSING", null));
    }

    @Test
    void getOrder_notFoundThrows() {
        when(endlessAisleOrderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getOrder(orderId));
    }

    @Test
    void updateStatus_insufficientStock_propagatesAndDoesNotSave() {
        order.setStatus("CONFIRMED");
        when(endlessAisleOrderRepository.findById(orderId)).thenReturn(Optional.of(order));
        doThrow(new BadRequestException("Insufficient inventory at node"))
                .when(inventoryService).adjustInventoryBySkuAtNode(eq(tenantId), eq("SKU-100"), eq(storeId), eq(-5));

        assertThrows(BadRequestException.class, () -> service.updateStatus(orderId, "PROCESSING", null));
        verify(endlessAisleOrderRepository, never()).save(any());
    }
}
