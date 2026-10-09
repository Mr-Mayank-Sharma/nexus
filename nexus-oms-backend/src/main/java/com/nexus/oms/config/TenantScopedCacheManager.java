package com.nexus.oms.config;

import com.nexus.oms.security.TenantContext;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prefixes every cache key with the current tenant id, so a {@code @Cacheable} key that
 * forgets the tenant (e.g. {@code 'list'} or a bare entity id) can never serve one tenant's
 * data to another. Calls without a tenant context (schedulers, startup) use a shared
 * {@code global} namespace that request traffic never reads.
 */
public class TenantScopedCacheManager implements CacheManager {

    static final String GLOBAL = "global";

    private final CacheManager delegate;
    private final Map<String, Cache> wrapped = new ConcurrentHashMap<>();

    public TenantScopedCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    public CacheManager getDelegate() {
        return delegate;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return null;
        }
        return wrapped.computeIfAbsent(name, n -> new TenantScopedCache(cache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    static String tenantPrefix() {
        try {
            UUID tenantId = TenantContext.getCurrentTenantId();
            return tenantId != null ? tenantId.toString() : GLOBAL;
        } catch (IllegalStateException e) {
            return GLOBAL;
        }
    }

    static final class TenantScopedCache implements Cache {

        private final Cache delegate;

        TenantScopedCache(Cache delegate) {
            this.delegate = delegate;
        }

        private static Object scoped(Object key) {
            return tenantPrefix() + "::" + key;
        }

        @Override public String getName() { return delegate.getName(); }
        @Override public Object getNativeCache() { return delegate.getNativeCache(); }
        @Override public ValueWrapper get(Object key) { return delegate.get(scoped(key)); }
        @Override public <T> T get(Object key, Class<T> type) { return delegate.get(scoped(key), type); }
        @Override public <T> T get(Object key, Callable<T> valueLoader) { return delegate.get(scoped(key), valueLoader); }
        @Override public void put(Object key, Object value) { delegate.put(scoped(key), value); }
        @Override public ValueWrapper putIfAbsent(Object key, Object value) { return delegate.putIfAbsent(scoped(key), value); }
        @Override public void evict(Object key) { delegate.evict(scoped(key)); }
        @Override public boolean evictIfPresent(Object key) { return delegate.evictIfPresent(scoped(key)); }
        @Override public void clear() { delegate.clear(); }
        @Override public boolean invalidate() { return delegate.invalidate(); }
    }
}
