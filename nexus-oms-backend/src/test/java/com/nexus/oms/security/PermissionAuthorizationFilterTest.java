package com.nexus.oms.security;

import com.nexus.oms.service.PermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionAuthorizationFilterTest {

    @Mock
    private PermissionService permissionService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private PermissionAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PermissionAuthorizationFilter(permissionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testPublicPathPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(permissionService);
    }

    @Test
    void testSwaggerPathPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testWebhookPathPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/webhooks/shopify");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testImportPathPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/import/orders");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testNoAuthenticationPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/orders");
        when(request.getMethod()).thenReturn("GET");

        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testAdminAlwaysPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/orders/123");
        when(request.getMethod()).thenReturn("DELETE");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("admin", UUID.randomUUID()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                ));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(permissionService);
    }

    @Test
    void testViewerWithNoPermissionGets403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/orders/123");
        when(request.getMethod()).thenReturn("DELETE");
        when(permissionService.resolveResource("/orders/123")).thenReturn("orders");
        when(permissionService.resolveAction("DELETE")).thenReturn("delete");
        when(permissionService.hasPermissionCached("VIEWER", "orders", "delete")).thenReturn(false);
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("viewer", tenantId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))
                ));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(response).getWriter();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testViewerWithPermissionPassesThrough() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/orders");
        when(request.getMethod()).thenReturn("GET");
        when(permissionService.resolveResource("/orders")).thenReturn("orders");
        when(permissionService.resolveAction("GET")).thenReturn("view");
        when(permissionService.hasPermissionCached("VIEWER", "orders", "view")).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("viewer", tenantId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))
                ));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testUnresolvablePathPassesThrough() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/some/unknown/path");
        when(request.getMethod()).thenReturn("GET");
        when(permissionService.resolveResource("/some/unknown/path")).thenReturn(null);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("user", tenantId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OPS_MANAGER"))
                ));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testHealthEndpointPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
