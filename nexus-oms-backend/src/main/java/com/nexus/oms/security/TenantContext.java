package com.nexus.oms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class TenantContext {

    private TenantContext() {}

    public static UUID getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal) {
            return principal.tenantId();
        }
        throw new IllegalStateException("No tenant context found in security context");
    }
}
