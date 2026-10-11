package com.nexus.oms.exception;

import com.nexus.oms.controller.ShippingLabelController;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.ShippingLabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ShippingLabelController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShippingLabelService shippingLabelService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", tenantId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void badRequest_returns400ApiError() throws Exception {
        when(shippingLabelService.generateCarrierLabel(any()))
                .thenThrow(new BadRequestException("carrier says no"));

        mockMvc.perform(post("/labels/carrier")
                        .contentType("application/json")
                        .content("{\"carrier\":\"FEDEX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("carrier says no"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void concurrentUpdate_returns409ApiError() throws Exception {
        when(shippingLabelService.generateCarrierLabel(any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("NxShipment", UUID.randomUUID()));

        mockMvc.perform(post("/labels/carrier")
                        .contentType("application/json")
                        .content("{\"carrier\":\"FEDEX\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This record was changed by someone else. Reload it and try again."));
    }

    @Test
    void dataIntegrityViolation_returns409NotServerError() throws Exception {
        when(shippingLabelService.generateCarrierLabel(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("null value in column user_id"));

        mockMvc.perform(post("/labels/carrier")
                        .contentType("application/json")
                        .content("{\"carrier\":\"FEDEX\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void illegalArgument_returns400_andIllegalState_returns409() throws Exception {
        when(shippingLabelService.generateCarrierLabel(any()))
                .thenThrow(new IllegalArgumentException("warehouseId is required"))
                .thenThrow(new IllegalStateException("Only DRAFT waves can be updated"));

        mockMvc.perform(post("/labels/carrier").contentType("application/json").content("{\"carrier\":\"FEDEX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("warehouseId is required"));
        mockMvc.perform(post("/labels/carrier").contentType("application/json").content("{\"carrier\":\"FEDEX\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only DRAFT waves can be updated"));
    }

    @Test
    void malformedBody_returns400_notA500() throws Exception {
        mockMvc.perform(post("/labels/carrier").contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void wrongMethodAndWrongContentType_return405And415() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/labels/carrier"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/labels/carrier").contentType("text/plain").content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void notFound_returns404ApiError() throws Exception {
        when(shippingLabelService.getLabel(any())).thenThrow(new ResourceNotFoundException("Label not found"));

        mockMvc.perform(get("/labels/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Label not found"));
    }

    @Test
    void typeMismatch_returns400ApiError() throws Exception {
        mockMvc.perform(get("/labels/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void missingParam_returns400ApiError() throws Exception {
        mockMvc.perform(post("/labels/bopis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing required parameter 'pickupOrderId'"));
    }

    @Test
    void genericException_returns500ApiError() throws Exception {
        when(shippingLabelService.getAllLabels()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/labels"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}