package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.Product;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import com.nexus.oms.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
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

@WebMvcTest(value = ProductController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

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
    void getProducts() throws Exception {
        when(productService.getProducts()).thenReturn(List.of(new Product()));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getProduct() throws Exception {
        UUID id = UUID.randomUUID();
        Product p = new Product();
        p.setId(id);
        when(productService.getProduct(id)).thenReturn(p);

        mockMvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void createProduct() throws Exception {
        when(productService.createProduct(any())).thenReturn(new Product());

        String body = "{\"sku\":\"SKU-001\",\"productName\":\"Test\",\"unitPrice\":10.5,\"isActive\":true}";
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProduct() throws Exception {
        when(productService.updateProduct(any(), any())).thenReturn(new Product());

        String body = "{\"sku\":\"SKU-002\",\"productName\":\"Updated\",\"unitPrice\":15.0,\"isActive\":true}";
        mockMvc.perform(put("/products/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteProduct() throws Exception {
        mockMvc.perform(delete("/products/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
