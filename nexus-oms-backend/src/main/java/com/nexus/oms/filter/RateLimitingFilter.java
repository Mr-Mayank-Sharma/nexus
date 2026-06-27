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
    private static final int MAX_REQUESTS_PER_WINDOW = 100;
    private static final int MAX_AUTH_REQUESTS = 10;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String key = resolveKey(req);
        int maxRequests = req.getRequestURI().contains("/auth/") ? MAX_AUTH_REQUESTS : MAX_REQUESTS_PER_WINDOW;

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

    private String resolveKey(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        String tenant = req.getHeader("X-Tenant-Id");
        return tenant != null ? tenant + ":" + ip : ip;
    }
}
