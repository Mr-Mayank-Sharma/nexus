package com.nexus.oms.entitlement;

import com.nexus.oms.entity.NxTenantSubscription;
import com.nexus.oms.repository.TenantSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves which product modules a tenant may use. Read on every API request, so results are
 * cached per tenant for {@link #TTL_MS}; plan changes take effect within a minute.
 */
@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);
    static final long TTL_MS = 60_000;

    public record Entitlements(Edition edition, Set<ProductModule> modules, LocalDateTime validUntil, boolean expired) {
        public boolean allows(ProductModule m) { return modules.contains(m); }
    }

    private record Cached(Entitlements value, long at) {}

    private final TenantSubscriptionRepository repository;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public EntitlementService(TenantSubscriptionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    EntitlementService(TenantSubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Entitlements forTenant(UUID tenantId) {
        long now = clock.millis();
        Cached c = cache.get(tenantId);
        if (c != null && now - c.at() < TTL_MS) return c.value();
        Entitlements e = resolve(repository.findById(tenantId).orElse(null));
        cache.put(tenantId, new Cached(e, now));
        return e;
    }

    public void evict(UUID tenantId) {
        cache.remove(tenantId);
    }

    Entitlements resolve(NxTenantSubscription sub) {
        if (sub == null) {
            // Tenants that predate licensing keep everything they had.
            Set<ProductModule> all = EnumSet.allOf(ProductModule.class);
            return new Entitlements(Edition.SUITE, Collections.unmodifiableSet(all), null, false);
        }
        Edition edition;
        try {
            edition = Edition.valueOf(sub.getEdition().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            log.error("Unknown edition '{}' for tenant {}; granting PLATFORM only", sub.getEdition(), sub.getTenantId());
            return new Entitlements(Edition.OMS, EnumSet.of(ProductModule.PLATFORM), sub.getValidUntil(), false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean expired = sub.getValidUntil() != null && sub.getValidUntil().isBefore(now);
        Set<ProductModule> modules = expired ? EnumSet.of(ProductModule.PLATFORM) : edition.modules();
        if (!expired && sub.getAddOns() != null) {
            for (String raw : sub.getAddOns().split(",")) {
                String name = raw.trim().toUpperCase(Locale.ROOT);
                if (name.isEmpty()) continue;
                try {
                    ProductModule m = ProductModule.valueOf(name);
                    if (ProductModule.ADD_ONS.contains(m)) modules.add(m);
                } catch (IllegalArgumentException ex) {
                    log.warn("Ignoring unknown add-on '{}' for tenant {}", raw, sub.getTenantId());
                }
            }
        }
        return new Entitlements(edition, Collections.unmodifiableSet(modules), sub.getValidUntil(), expired);
    }
}
