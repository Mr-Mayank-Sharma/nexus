package com.nexus.oms.tenancy;

import com.nexus.core.tenancy.TenantScope;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** The core's view of which company it works for: the signed-in one, or the one a job picked (doc 29, phase B). */
@Component
public class SignedInTenantScope implements TenantScope {

    private final TenantDatabaseRegistry registry;

    public SignedInTenantScope(TenantDatabaseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String currentDatabaseKey() {
        UUID tenantId = TenantContext.findCurrentTenantId();
        return registry.hasOwnDatabase(tenantId) ? tenantId.toString() : SHARED;
    }

    @Override
    public List<String> companiesWithOwnDatabase() {
        return registry.tenantsWithOwnDatabase().stream().map(UUID::toString).toList();
    }

    @Override
    public <T> T callAs(String companyId, Supplier<T> work) {
        return TenantContext.callAs(UUID.fromString(companyId), work);
    }
}
