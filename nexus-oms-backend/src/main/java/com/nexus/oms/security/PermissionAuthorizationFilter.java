package com.nexus.oms.security;

import com.nexus.oms.service.PermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class PermissionAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PermissionAuthorizationFilter.class);
    private static final int FORBIDDEN = 403;

    private static final List<String> PUBLIC_PATHS = List.of(
        "/auth/", "/swagger-ui/", "/v3/api-docs/", "/actuator/health", "/webhooks/"
    );

    private static final List<String> PERMIT_ALL_PATHS = List.of();

    private final PermissionService permissionService;

    public PermissionAuthorizationFilter(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        if (isPublicOrPermitAll(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        String role = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .findFirst()
            .map(a -> a.substring(5))
            .orElse(null);

        if (role == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("ADMIN".equals(role) || "IMPORT_TOKEN".equals(role)) {
            filterChain.doFilter(request, response);
            return;
        }

        String resource = permissionService.resolveResource(path);
        if (resource == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String action = permissionService.resolveAction(method);
        if (action == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean permitted = permissionService.hasPermissionCached(role, resource, action);
        if (!permitted) {
            log.warn("Permission denied: role={} resource={} action={} path={} tenantId={}",
                role, resource, action, path, MDC.get("tenantId"));
            response.setStatus(FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions for " + resource + ":" + action + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicOrPermitAll(String path) {
        for (String p : PUBLIC_PATHS) {
            if (path.startsWith(p)) return true;
        }
        for (String p : PERMIT_ALL_PATHS) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }
}
