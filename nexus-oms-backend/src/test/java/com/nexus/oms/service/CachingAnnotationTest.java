package com.nexus.oms.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CachingAnnotationTest {

    @Test
    void productService_getProducts_HasCacheableAnnotation() throws Exception {
        Method method = ProductService.class.getMethod("getProducts");
        Cacheable annotation = method.getAnnotation(Cacheable.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"products"}, annotation.value());
    }

    @Test
    void productService_createProduct_HasCacheEvictAnnotation() throws Exception {
        Method method = ProductService.class.getMethod("createProduct", com.nexus.oms.entity.Product.class);
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"products"}, annotation.value());
        assertTrue(annotation.allEntries());
    }

    @Test
    void inventoryService_getAvailableToPromise_HasCacheableAnnotation() throws Exception {
        Method method = InventoryService.class.getMethod("getAvailableToPromise", java.util.UUID.class, String.class);
        Cacheable annotation = method.getAnnotation(Cacheable.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"inventory"}, annotation.value());
    }

    @Test
    void dashboardService_getOrderVelocity_HasCacheableAnnotation() throws Exception {
        Method method = DashboardService.class.getMethod("getOrderVelocity");
        Cacheable annotation = method.getAnnotation(Cacheable.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"dashboard"}, annotation.value());
    }

    @Test
    void warehouseService_getZones_HasCacheableAnnotation() throws Exception {
        Method method = WarehouseService.class.getMethod("getZones", java.util.UUID.class);
        Cacheable annotation = method.getAnnotation(Cacheable.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"warehouseZones"}, annotation.value());
    }

    @Test
    void orderService_getOrder_HasCacheableAnnotation() throws Exception {
        Method method = OrderService.class.getMethod("getOrder", java.util.UUID.class);
        Cacheable annotation = method.getAnnotation(Cacheable.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"orders"}, annotation.value());
    }

    @Test
    void orderService_createOrder_HasCacheEvictAnnotation() throws Exception {
        Method method = OrderService.class.getMethod("createOrder", java.util.UUID.class,
                com.nexus.oms.dto.OrderRequest.class);
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        assertNotNull(annotation);
        assertArrayEquals(new String[]{"orders"}, annotation.value());
        assertTrue(annotation.allEntries());
    }
}
