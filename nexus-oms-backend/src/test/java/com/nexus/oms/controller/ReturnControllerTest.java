package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ReturnResponse;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.ReturnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReturnController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReturnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReturnService returnService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID tenantId;
    private UUID returnId;
    private ReturnResponse testReturnResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        returnId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", tenantId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        testReturnResponse = ReturnResponse.builder()
                .id(returnId)
                .rmaNumber("RMA-001")
                .orderId(UUID.randomUUID())
                .status("REQUESTED")
                .reason("DEFECTIVE")
                .build();
    }

    @Test
    void testGetReturns_ReturnsList() throws Exception {
        when(returnService.getReturns(any(), any())).thenReturn(List.of(testReturnResponse));

        mockMvc.perform(get("/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(returnId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("REQUESTED"));
    }

    @Test
    void testGetReturn_ReturnsReturn() throws Exception {
        when(returnService.getReturn(returnId)).thenReturn(testReturnResponse);

        mockMvc.perform(get("/returns/" + returnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(returnId.toString()));
    }

    @Test
    void testGetKPIs_ReturnsMap() throws Exception {
        when(returnService.getReturnKPIs(any())).thenReturn(Map.of("total", 10, "pending", 3));

        mockMvc.perform(get("/returns/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(10));
    }

    @Test
    void testGetReturnReasons_ReturnsList() throws Exception {
        when(returnService.getReturnReasons(any())).thenReturn(
                List.of(Map.of("reason", "DEFECTIVE", "count", 5)));

        mockMvc.perform(get("/returns/reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reason").value("DEFECTIVE"));
    }

    @Test
    void testCreateReturn_CreatesReturn() throws Exception {
        when(returnService.createReturn(any(), any())).thenReturn(testReturnResponse);

        String body = """
                {
                    "orderId": "%s",
                    "customerId": "%s",
                    "reason": "DEFECTIVE",
                    "items": []
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testApproveReturn_Approves() throws Exception {
        testReturnResponse.setStatus("APPROVED");
        when(returnService.approveReturn(any(), any())).thenReturn(testReturnResponse);

        mockMvc.perform(post("/returns/" + returnId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void testRejectReturn_Rejects() throws Exception {
        testReturnResponse.setStatus("REJECTED");
        when(returnService.rejectReturn(any(), any())).thenReturn(testReturnResponse);

        String body = "{\"reason\": \"Item not eligible\"}";
        mockMvc.perform(post("/returns/" + returnId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }
}
