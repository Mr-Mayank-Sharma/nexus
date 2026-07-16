package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.OrderResponse;
import com.nexus.oms.dto.OrderStatusUpdateRequest;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID tenantId;
    private UUID orderId;
    private OrderResponse testOrderResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", tenantId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        testOrderResponse = OrderResponse.builder()
                .id(orderId)
                .tenantId(tenantId)
                .status("PENDING")
                .channel("WEB")
                .subtotal(new BigDecimal("20.00"))
                .total(new BigDecimal("27.00"))
                .build();
    }

    @Test
    void testGetOrders_ReturnsPage() throws Exception {
        Page<OrderResponse> page = new PageImpl<>(List.of(testOrderResponse));
        when(orderService.getOrders(any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
    }

    @Test
    void testGetOrder_ReturnsOrder() throws Exception {
        when(orderService.getOrder(orderId)).thenReturn(testOrderResponse);

        mockMvc.perform(get("/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));
    }

    @Test
    void testConfirmOrder_ReturnsConfirmed() throws Exception {
        testOrderResponse.setStatus("CONFIRMED");
        when(orderService.confirmOrder(orderId)).thenReturn(testOrderResponse);

        mockMvc.perform(post("/orders/" + orderId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void testCancelOrder_ReturnsCancelled() throws Exception {
        testOrderResponse.setStatus("CANCELLED");
        when(orderService.cancelOrder(orderId)).thenReturn(testOrderResponse);

        mockMvc.perform(post("/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void testUpdateStatus_ReturnsUpdated() throws Exception {
        testOrderResponse.setStatus("SHIPPED");
        when(orderService.updateStatus(any(), any(), any(), any(), any())).thenReturn(testOrderResponse);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest("SHIPPED", "DISPATCHED", "TRACK-1", "FEDEX");
        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }
}
