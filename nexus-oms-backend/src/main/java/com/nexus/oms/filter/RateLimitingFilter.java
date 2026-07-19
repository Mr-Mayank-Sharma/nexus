package com.nexus.oms.filter;

import com.nexus.oms.security.TenantContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final long WINDOW_MS = 1000;

    private static final int TIER_GENERAL = 200;
    private static final int TIER_STANDARD = 100;
    private static final int TIER_AUTH = 10;
    private static final int TIER_IMPORT = 5;
    private static final int TIER_AI_CHAT = 20;

    private final StringRedisTemplate redisTemplate;

    public RateLimitingFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        try {
            String key = resolveKey(req);
            int maxRequests = resolveTier(req.getRequestURI(), req.getMethod());

            String redisKey = "ratelimit:" + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, WINDOW_MS, TimeUnit.MILLISECONDS);
            }

            res.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            long remaining = Math.max(0, maxRequests - (count != null ? count : 0));
            res.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

            if (count != null && count > maxRequests) {
                res.setStatus(429);
                res.setHeader("Retry-After", "1");
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limiting unavailable (Redis down?), allowing request: {}", e.getMessage());
        }

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
        try {
            UUID tenantId = TenantContext.getCurrentTenantId();
            return tenantId.toString() + ":" + ip;
        } catch (IllegalStateException e) {
            return ip;
        }
    }
}
