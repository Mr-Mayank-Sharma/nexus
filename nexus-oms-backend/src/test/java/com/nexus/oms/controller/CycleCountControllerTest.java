package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxCycleCount;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.CycleCountService;
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

@WebMvcTest(value = CycleCountController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class CycleCountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CycleCountService cycleCountService;

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
    void getCycleCounts() throws Exception {
        Page<NxCycleCount> page = new PageImpl<>(List.of(new NxCycleCount()));
        when(cycleCountService.getCycleCounts(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/cycle-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCycleCounts_withStatus() throws Exception {
        Page<NxCycleCount> page = new PageImpl<>(List.of(new NxCycleCount()));
        when(cycleCountService.getCycleCounts(any(), eq("PENDING"), any())).thenReturn(page);

        mockMvc.perform(get("/cycle-counts").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCycleCount() throws Exception {
        UUID id = UUID.randomUUID();
        NxCycleCount cc = new NxCycleCount();
        cc.setId(id);
        when(cycleCountService.getCycleCount(id)).thenReturn(cc);

        mockMvc.perform(get("/cycle-counts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void createCycleCount() throws Exception {
        when(cycleCountService.createCycleCount(any(), any())).thenReturn(new NxCycleCount());

        String body = "{\"nodeId\":\"" + UUID.randomUUID() + "\",\"sku\":\"SKU-001\",\"expectedQty\":10}";
        mockMvc.perform(post("/cycle-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void performCount() throws Exception {
        when(cycleCountService.performCount(any(), any(), any())).thenReturn(new NxCycleCount());

        mockMvc.perform(post("/cycle-counts/" + UUID.randomUUID() + "/count")
                        .param("countedQty", "5")
                        .param("countedBy", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
