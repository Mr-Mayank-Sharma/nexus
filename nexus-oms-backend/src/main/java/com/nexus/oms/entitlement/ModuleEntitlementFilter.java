package com.nexus.oms.entitlement;

import com.nexus.oms.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects API calls to a module the tenant's plan does not include with 403 and a
 * machine-readable body, so the UI can show an upgrade prompt rather than an error. Applies to
 * every role: an administrator cannot use a module the organisation has not bought.
 */
public class ModuleEntitlementFilter extends OncePerRequestFilter {

    private final EntitlementService entitlements;

    public ModuleEntitlementFilter(EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID tenantId = TenantContext.findCurrentTenantId();
        if (tenantId == null || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        ProductModule module = ProductModule.forApiPath(path);
        if (module == ProductModule.PLATFORM || entitlements.forTenant(tenantId).allows(module)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"MODULE_NOT_LICENSED\",\"module\":\"" + module
                + "\",\"message\":\"Your plan does not include " + module + ". Contact your administrator to add it.\"}");
    }
}
