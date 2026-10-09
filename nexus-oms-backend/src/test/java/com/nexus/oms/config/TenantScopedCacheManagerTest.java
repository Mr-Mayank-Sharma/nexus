package com.nexus.oms.config;

import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantScopedCacheManagerTest {

    private final TenantScopedCacheManager manager =
            new TenantScopedCacheManager(new ConcurrentMapCacheManager("products"));

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void sameKeyIsIsolatedPerTenant() {
        Cache cache = manager.getCache("products");
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        TenantContext.setCurrentTenantId(tenantA);
        cache.put("list", "catalogue-of-A");

        TenantContext.setCurrentTenantId(tenantB);
        assertNull(cache.get("list"), "tenant B must not see tenant A's cached value");
        cache.put("list", "catalogue-of-B");

        TenantContext.setCurrentTenantId(tenantA);
        assertEquals("catalogue-of-A", cache.get("list").get());
    }

    @Test
    void noTenantUsesSeparateGlobalNamespace() {
        Cache cache = manager.getCache("products");
        cache.put("k", "global-value");

        TenantContext.setCurrentTenantId(UUID.randomUUID());
        assertNull(cache.get("k"));
    }

    @Test
    void evictIsScopedToo() {
        Cache cache = manager.getCache("products");
        UUID tenantA = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantA);
        cache.put("k", "a");
        TenantContext.setCurrentTenantId(UUID.randomUUID());
        cache.evict("k");
        TenantContext.setCurrentTenantId(tenantA);
        assertNotNull(cache.get("k"));
    }
}
