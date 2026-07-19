package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxInventoryReceipt;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.InventoryReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = InventoryReceiptController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class InventoryReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryReceiptService inventoryReceiptService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", UUID.randomUUID()), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void getReceipts() throws Exception {
        Page<NxInventoryReceipt> page = new PageImpl<>(List.of(new NxInventoryReceipt()));
        when(inventoryReceiptService.getReceipts(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/inventory-receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getReceipt() throws Exception {
        UUID id = UUID.randomUUID();
        NxInventoryReceipt receipt = new NxInventoryReceipt();
        receipt.setId(id);
        when(inventoryReceiptService.getReceipt(id)).thenReturn(receipt);

        mockMvc.perform(get("/inventory-receipts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void createReceipt() throws Exception {
        when(inventoryReceiptService.createReceipt(any(), any())).thenReturn(new NxInventoryReceipt());

        String body = "{\"nodeId\":\"" + UUID.randomUUID() + "\",\"receiptType\":\"PURCHASE_ORDER\",\"sku\":\"SKU-001\",\"quantity\":100}";
        mockMvc.perform(post("/inventory-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void receiveInventory() throws Exception {
        when(inventoryReceiptService.receiveInventory(any(), any())).thenReturn(new NxInventoryReceipt());

        mockMvc.perform(post("/inventory-receipts/" + UUID.randomUUID() + "/receive")
                        .param("receivedBy", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteReceipt() throws Exception {
        mockMvc.perform(delete("/inventory-receipts/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
