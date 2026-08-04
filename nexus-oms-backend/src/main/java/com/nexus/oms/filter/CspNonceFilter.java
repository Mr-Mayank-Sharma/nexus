package com.nexus.oms.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates a cryptographically secure nonce per request and stores it
 * as a request attribute for use in Content-Security-Policy headers and
 * CSP-aware templates/scripts.
 */
@Component
@Order(0)
public class CspNonceFilter implements Filter {

    public static final String NONCE_REQUEST_ATTR = "cspNonce";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String nonce = generateNonce();
        req.setAttribute(NONCE_REQUEST_ATTR, nonce);

        // Expose nonce to frontend via response header (for X-Nonce pattern)
        res.setHeader("X-CSP-Nonce", nonce);

        // Build nonce-aware CSP
        String csp = "default-src 'self'; " +
                "script-src 'self' 'nonce-" + nonce + "'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: blob:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'; " +
                "report-uri /csp-report; " +
                "report-to csp-endpoint";
        res.setHeader("Content-Security-Policy", csp);

        chain.doFilter(request, response);
    }

    private String generateNonce() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
