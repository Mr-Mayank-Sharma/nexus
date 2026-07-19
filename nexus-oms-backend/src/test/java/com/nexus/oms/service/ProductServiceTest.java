package com.nexus.oms.service;

import com.nexus.oms.entity.Product;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ProductRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;
    private UUID tenantId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
        tenantId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    @Test
    void getProducts() {
        Product p1 = new Product();
        p1.setTenantId(tenantId);
        Product p2 = new Product();
        p2.setTenantId(tenantId);
        Product p3 = new Product();
        p3.setTenantId(UUID.randomUUID());

        when(productRepository.findAll()).thenReturn(List.of(p1, p2, p3));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);
            List<Product> result = productService.getProducts();
            assertEquals(2, result.size());
        }
    }

    @Test
    void getProduct_found() {
        Product product = new Product();
        product.setId(productId);
        product.setTenantId(tenantId);

        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);
            assertSame(product, productService.getProduct(productId));
        }
    }

    @Test
    void getProduct_notFound_throws() {
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.empty());

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);
            assertThrows(ResourceNotFoundException.class, () -> productService.getProduct(productId));
        }
    }

    @Test
    void createProduct() {
        Product input = new Product();
        input.setSku("SKU-001");

        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            Product result = productService.createProduct(input);

            assertNull(result.getId());
            assertEquals(tenantId, result.getTenantId());
            assertEquals("SKU-001", result.getSku());
        }
    }

    @Test
    void updateProduct() {
        Product existing = new Product();
        existing.setId(productId);
        existing.setSku("OLD-SKU");
        existing.setProductName("Old Name");

        Product updates = new Product();
        updates.setSku("NEW-SKU");
        updates.setProductName("New Name");

        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            Product result = productService.updateProduct(productId, updates);

            assertEquals("NEW-SKU", result.getSku());
            assertEquals("New Name", result.getProductName());
        }
    }

    @Test
    void deleteProduct() {
        Product product = new Product();
        product.setId(productId);
        product.setTenantId(tenantId);

        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);

            productService.deleteProduct(productId);

            verify(productRepository).delete(product);
        }
    }
}
