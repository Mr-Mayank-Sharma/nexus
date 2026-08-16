package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.AsnRequest;
import com.nexus.oms.entity.NxAsn;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.AsnService;
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

@WebMvcTest(value = AsnController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class AsnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AsnService asnService;

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
    void getAsns() throws Exception {
        Page<NxAsn> page = new PageImpl<>(List.of(new NxAsn()));
        when(asnService.getAsns(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/asn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getAsn() throws Exception {
        UUID id = UUID.randomUUID();
        NxAsn asn = new NxAsn();
        asn.setId(id);
        when(asnService.getAsn(id)).thenReturn(asn);

        mockMvc.perform(get("/asn/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createAsn() throws Exception {
        NxAsn asn = new NxAsn();
        asn.setAsnNumber("ASN-1001");
        when(asnService.createAsn(any(), any())).thenReturn(asn);

        AsnRequest req = new AsnRequest();
        req.setAsnNumber("ASN-1001");
        AsnRequest.AsnLineRequest line = new AsnRequest.AsnLineRequest();
        line.setSku("SKU-1");
        line.setExpectedQty(10);
        req.setLines(List.of(line));

        mockMvc.perform(post("/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createAsn_validationRejectsMissingFields() throws Exception {
        AsnRequest req = new AsnRequest();
        req.setAsnNumber("ASN-1002");
        req.setLines(List.of(new AsnRequest.AsnLineRequest()));

        mockMvc.perform(post("/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void receiveLine() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        NxAsn asn = new NxAsn();
        asn.setId(asnId);
        when(asnService.receiveLine(asnId, lineId, 5, "testuser")).thenReturn(asn);

        mockMvc.perform(post("/asn/" + asnId + "/lines/" + lineId + "/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("receivedQty", 5)))
                        .param("receivedBy", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void receiveLine_missingQuantityRejected() throws Exception {
        UUID asnId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        mockMvc.perform(post("/asn/" + asnId + "/lines/" + lineId + "/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void closeAsn() throws Exception {
        UUID id = UUID.randomUUID();
        NxAsn asn = new NxAsn();
        asn.setId(id);
        asn.setStatus("CLOSED");
        when(asnService.closeAsn(id)).thenReturn(asn);

        mockMvc.perform(post("/asn/" + id + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteAsn() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/asn/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
