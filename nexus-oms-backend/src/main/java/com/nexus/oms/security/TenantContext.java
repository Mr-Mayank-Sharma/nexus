package com.nexus.oms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class TenantContext {

    private TenantContext() {}

    private static final ThreadLocal<UUID> OVERRIDE_TENANT_ID = new ThreadLocal<>();

    public static UUID getCurrentTenantId() {
        UUID override = OVERRIDE_TENANT_ID.get();
        if (override != null) {
            return override;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal) {
            return principal.tenantId();
        }
        throw new IllegalStateException("No tenant context found in security context");
    }

    /** The current tenant, or null on unauthenticated requests (sign-in, sign-up, health). */
    public static UUID findCurrentTenantId() {
        UUID override = OVERRIDE_TENANT_ID.get();
        if (override != null) {
            return override;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal ? principal.tenantId() : null;
    }

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal) {
            return UUID.nameUUIDFromBytes(principal.username().getBytes());
        }
        if (auth != null && auth.getName() != null) {
            return UUID.nameUUIDFromBytes(auth.getName().getBytes());
        }
        return UUID.nameUUIDFromBytes("system".getBytes());
    }

    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal) {
            return principal.username();
        }
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "system";
    }

    public static void setCurrentTenantId(UUID tenantId) {
        OVERRIDE_TENANT_ID.set(tenantId);
    }

    public static void clear() {
        OVERRIDE_TENANT_ID.remove();
    }

    /** Runs the work as the company, then puts back exactly the override that was there before (none, or another company). */
    public static <T> T callAs(UUID tenantId, java.util.function.Supplier<T> work) {
        UUID previousOverride = OVERRIDE_TENANT_ID.get();
        OVERRIDE_TENANT_ID.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previousOverride == null) {
                OVERRIDE_TENANT_ID.remove();
            } else {
                OVERRIDE_TENANT_ID.set(previousOverride);
            }
        }
    }
}
