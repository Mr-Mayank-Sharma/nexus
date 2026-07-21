package com.nexus.oms.service;

import com.nexus.oms.dto.*;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.kafka.KafkaProducerService;
import com.nexus.oms.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private KafkaProducerService kafkaProducerService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private NodeRepository nodeRepository;

    private OrderService orderService;
    private UUID tenantId;
    private UUID orderId;
    private NxOrder testOrder;
    private NxOrderItem testItem;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, customerRepository,
                addressRepository, inventoryService, kafkaProducerService, objectMapper, nodeRepository);
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        testItem = NxOrderItem.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .sku("SKU-001")
                .productName("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("20.00"))
                .allocatedQty(0)
                .build();

        testOrder = NxOrder.builder()
                .id(orderId)
                .tenantId(tenantId)
                .customerId(UUID.randomUUID())
                .channel("WEB")
                .status("PENDING")
                .subtotal(new BigDecimal("20.00"))
                .shippingCost(new BigDecimal("5.00"))
                .taxAmount(new BigDecimal("2.00"))
                .total(new BigDecimal("27.00"))
                .build();
    }

    @Test
    void testGetOrder_WhenExists_ReturnsOrderResponse() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        OrderResponse result = orderService.getOrder(orderId);

        assertEquals(orderId, result.getId());
        assertEquals("PENDING", result.getStatus());
        verify(orderRepository).findById(orderId);
    }

    @Test
    void testGetOrder_WhenNotExists_ThrowsException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrder(orderId));
    }

    @Test
    void testGetOrders_WithPagination_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<NxOrder> page = new PageImpl<>(List.of(testOrder));
        when(orderRepository.findByTenantId(tenantId, pageable)).thenReturn(page);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        Page<OrderResponse> result = orderService.getOrders(tenantId, null, null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("PENDING", result.getContent().get(0).getStatus());
        verify(orderRepository).findByTenantId(tenantId, pageable);
    }

    @Test
    void testGetOrders_WithStatusFilter_ReturnsFilteredPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<NxOrder> page = new PageImpl<>(List.of(testOrder));
        when(orderRepository.findByTenantIdAndStatus(tenantId, "PENDING", pageable)).thenReturn(page);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        Page<OrderResponse> result = orderService.getOrders(tenantId, "PENDING", null, pageable);

        assertEquals(1, result.getContent().size());
        verify(orderRepository).findByTenantIdAndStatus(tenantId, "PENDING", pageable);
    }

    @Test
    void testGetOrders_WithSearch_ReturnsSearchPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<NxOrder> page = new PageImpl<>(List.of(testOrder));
        when(orderRepository.search(tenantId, "test", pageable)).thenReturn(page);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        Page<OrderResponse> result = orderService.getOrders(tenantId, null, "test", pageable);

        assertEquals(1, result.getContent().size());
        verify(orderRepository).search(tenantId, "test", pageable);
    }

    @Test
    void testCreateOrder_WithNewCustomer_CreatesOrder() throws Exception {
        OrderRequest request = new OrderRequest();
        request.setCustomerEmail("test@example.com");
        request.setCustomerName("Test User");
        request.setShippingAddress(new OrderRequest.ShippingAddress("123 Main St", null, "NYC", "NY", "10001", "US", null, null));
        request.setChannel("WEB");

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setSku("SKU-001");
        itemReq.setProductName("Test Product");
        itemReq.setQuantity(2);
        itemReq.setUnitPrice(new BigDecimal("10.00"));
        request.setItems(List.of(itemReq));

        NxCustomer newCustomer = NxCustomer.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Test User")
                .email("test@example.com")
                .build();

        doReturn("{}").when(objectMapper).writeValueAsString(any());
        when(customerRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(NxCustomer.class))).thenReturn(newCustomer);
        when(orderRepository.save(any(NxOrder.class))).thenAnswer(i -> {
            NxOrder o = i.getArgument(0);
            if (o.getId() == null) o.setId(orderId);
            return o;
        });
        when(orderItemRepository.save(any(NxOrderItem.class))).thenReturn(testItem);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        OrderResponse result = orderService.createOrder(tenantId, request);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(kafkaProducerService).publish(eq("order.created"), anyString());
    }

    @Test
    void testCreateOrder_WithExistingCustomer_CreatesOrder() throws Exception {
        OrderRequest request = new OrderRequest();
        request.setCustomerEmail("existing@example.com");
        request.setCustomerName("Existing User");
        request.setShippingAddress(new OrderRequest.ShippingAddress("456 Oak Ave", null, "LA", "CA", "90001", "US", null, null));
        request.setChannel("WEB");

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setSku("SKU-002");
        itemReq.setProductName("Another Product");
        itemReq.setQuantity(1);
        itemReq.setUnitPrice(new BigDecimal("25.00"));
        request.setItems(List.of(itemReq));

        UUID customerId = UUID.randomUUID();
        NxCustomer existingCustomer = NxCustomer.builder()
                .id(customerId)
                .tenantId(tenantId)
                .name("Existing User")
                .email("existing@example.com")
                .build();

        doReturn("{}").when(objectMapper).writeValueAsString(any());
        when(customerRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingCustomer));
        when(orderRepository.save(any(NxOrder.class))).thenAnswer(i -> {
            NxOrder o = i.getArgument(0);
            if (o.getId() == null) o.setId(orderId);
            return o;
        });
        when(orderItemRepository.save(any(NxOrderItem.class))).thenReturn(testItem);
        when(orderItemRepository.findByOrderId(any())).thenReturn(List.of(testItem));

        OrderResponse result = orderService.createOrder(tenantId, request);

        assertNotNull(result);
        verify(customerRepository, never()).save(any());
        verify(kafkaProducerService).publish(eq("order.created"), anyString());
    }

    @Test
    void testUpdateStatus_UpdatesFields() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(NxOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        OrderResponse result = orderService.updateStatus(orderId, "SHIPPED", "DISPATCHED", "TRACK-123", "FEDEX");

        assertEquals("SHIPPED", result.getStatus());
        verify(kafkaProducerService).publish("order.shipped", orderId.toString());
    }

    @Test
    void testConfirmOrder_ChangesStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(NxOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));

        OrderResponse result = orderService.confirmOrder(orderId);

        assertEquals("CONFIRMED", result.getStatus());
        verify(kafkaProducerService).publish("order.confirmed", orderId.toString());
    }

    @Test
    void testCancelOrder_WithoutAllocation_Cancels() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));
        when(orderRepository.save(any(NxOrder.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse result = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", result.getStatus());
        verify(kafkaProducerService).publish("order.cancelled", orderId.toString());
        verify(inventoryService, never()).releaseInventory(any(), any(), any(), anyInt());
    }

    @Test
    void testCancelOrder_WithAllocation_ReleasesInventory() {
        testItem.setAllocatedNodeId(UUID.randomUUID());
        testItem.setAllocatedQty(2);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(testItem));
        when(orderRepository.save(any(NxOrder.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse result = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", result.getStatus());
        verify(inventoryService).releaseInventory(tenantId, "SKU-001", testItem.getAllocatedNodeId(), 2);
        verify(kafkaProducerService).publish("order.cancelled", orderId.toString());
    }
}
