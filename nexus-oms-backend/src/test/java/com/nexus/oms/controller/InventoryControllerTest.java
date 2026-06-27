package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    private UUID tenantId;
    private NxInventory testInventory;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", tenantId), null, List.of()
                )
        );
        testInventory = NxInventory.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .sku("TEST-SKU-001")
                .quantityOnHand(100)
                .build();
    }

    @Test
    void testGetInventory_ReturnsList() throws Exception {
        when(inventoryService.getInventoryByTenant(any())).thenReturn(List.of(testInventory));
        mockMvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sku").value("TEST-SKU-001"));
    }

    @Test
    void testGetBySku_ReturnsInventory() throws Exception {
        when(inventoryService.getBySku(any(), eq("TEST-SKU-001"))).thenReturn(testInventory);
        mockMvc.perform(get("/inventory/TEST-SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("TEST-SKU-001"));
    }
}
