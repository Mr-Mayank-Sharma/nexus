package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxCarrier;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.CarrierService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = CarrierController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class CarrierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CarrierService carrierService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", tenantId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void getCarriers() throws Exception {
        Page<NxCarrier> page = new PageImpl<>(List.of(new NxCarrier()));
        when(carrierService.getCarriers(any(), any())).thenReturn(page);

        mockMvc.perform(get("/carriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCarrier() throws Exception {
        UUID id = UUID.randomUUID();
        NxCarrier carrier = new NxCarrier();
        carrier.setId(id);
        when(carrierService.getCarrier(id)).thenReturn(carrier);

        mockMvc.perform(get("/carriers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void createCarrier() throws Exception {
        when(carrierService.createCarrier(any())).thenReturn(new NxCarrier());

        String body = "{\"name\":\"FedEx\",\"code\":\"FDX\"}";
        mockMvc.perform(post("/carriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateCarrier() throws Exception {
        UUID id = UUID.randomUUID();
        when(carrierService.updateCarrier(any(), any())).thenReturn(new NxCarrier());

        String body = "{\"name\":\"UPS\"}";
        mockMvc.perform(put("/carriers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteCarrier() throws Exception {
        mockMvc.perform(delete("/carriers/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getKPIs() throws Exception {
        when(carrierService.getCarrierKPIs(any())).thenReturn(Map.of("activeCarriers", 5));

        mockMvc.perform(get("/carriers/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCarriers").value(5));
    }
}
