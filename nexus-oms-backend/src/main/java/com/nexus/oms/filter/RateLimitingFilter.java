package com.nexus.oms.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    static class WindowCounter {
        final long windowStart;
        final AtomicInteger count;

        WindowCounter(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }
    }

    private static final long WINDOW_MS = 1000;

    // Per-endpoint rate limit tiers (requests per second)
    private static final int TIER_GENERAL = 200;     // read-only GETs, health
    private static final int TIER_STANDARD = 100;    // standard CRUD
    private static final int TIER_AUTH = 10;         // login, forgot-password
    private static final int TIER_IMPORT = 5;        // file uploads, bulk imports
    private static final int TIER_AI_CHAT = 20;      // AI chat completions

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();
        String method = req.getMethod();

        String key = resolveKey(req);
        int maxRequests = resolveTier(path, method);

        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.windowStart) >= WINDOW_MS) {
                return new WindowCounter(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > maxRequests) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        res.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(maxRequests - counter.count.get()));
        res.setHeader("X-RateLimit-Reset", String.valueOf(counter.windowStart + WINDOW_MS));

        chain.doFilter(request, response);
    }

    private int resolveTier(String path, String method) {
        if (path.contains("/auth/")) return TIER_AUTH;
        if (path.contains("/import/")) return TIER_IMPORT;
        if (path.contains("/ai/chat")) return TIER_AI_CHAT;
        if ("GET".equalsIgnoreCase(method)) return TIER_GENERAL;
        return TIER_STANDARD;
    }

    private String resolveKey(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        String tenant = req.getHeader("X-Tenant-Id");
        return tenant != null ? tenant + ":" + ip : ip;
    }
}
