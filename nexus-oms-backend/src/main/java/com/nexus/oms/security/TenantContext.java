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
}
