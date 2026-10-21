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

/**
 * {@code /v1/**} needs a client API key ({@code X-Nexus-Gateway-Key}); the client id goes on the request.
 * {@code /admin/**} needs the operator token ({@code X-Admin-Token}, from {@code NEXUS_GATEWAY_ADMIN_TOKEN}).
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String CLIENT = "nexus.gateway.client";

    private final Clients clients;
    private final String adminToken;

    public AuthFilter(Clients clients, @Value("${nexus.gateway.admin-token:}") String adminToken) {
        this.clients = clients;
        this.adminToken = adminToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/v1/")) {
            var client = clients.authenticate(req.getHeader("X-Nexus-Gateway-Key"));
            if (client.isEmpty()) {
                deny(res, "A valid X-Nexus-Gateway-Key header is required");
                return;
            }
            req.setAttribute(CLIENT, client.get());
        } else if (path.startsWith("/admin/")) {
            String given = req.getHeader("X-Admin-Token");
            if (adminToken == null || adminToken.length() < 16 || given == null
                    || !MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
                deny(res, "A valid X-Admin-Token header is required");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static void deny(HttpServletResponse res, String message) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
