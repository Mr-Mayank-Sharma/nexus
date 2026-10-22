package com.nexus.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * {@code /v1/**} needs a client API key ({@code X-Nexus-Gateway-Key}); the client id goes on the request.
 * {@code /admin/**} needs the operator token ({@code X-Admin-Token}, from {@code NEXUS_GATEWAY_ADMIN_TOKEN}).
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String CLIENT = "nexus.gateway.client";
    static final String CLIENT_KEY_HEADER = "X-Nexus-Gateway-Key";
    static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final int MIN_ADMIN_TOKEN_LENGTH = 16;

    private final Clients clients;
    private final String adminToken;

    public AuthFilter(Clients clients, @Value("${nexus.gateway.admin-token:}") String adminToken) {
        this.clients = clients;
        this.adminToken = adminToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/v1/")) {
            Optional<String> clientId = clients.authenticate(request.getHeader(CLIENT_KEY_HEADER));
            if (clientId.isEmpty()) {
                deny(response, "A valid X-Nexus-Gateway-Key header is required");
                return;
            }
            request.setAttribute(CLIENT, clientId.get());
        } else if (path.startsWith("/admin/")) {
            if (!isAdminToken(request.getHeader(ADMIN_TOKEN_HEADER))) {
                deny(response, "A valid X-Admin-Token header is required");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Compared in constant time; a missing or too short configured token lets no one in. */
    private boolean isAdminToken(String givenToken) {
        boolean configured = adminToken != null && adminToken.length() >= MIN_ADMIN_TOKEN_LENGTH;
        if (!configured || givenToken == null) {
            return false;
        }
        return MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8), givenToken.getBytes(StandardCharsets.UTF_8));
    }

    private static void deny(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
