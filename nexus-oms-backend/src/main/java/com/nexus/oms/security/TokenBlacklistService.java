package com.nexus.oms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenBlacklistService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void blacklist(String jti, long expirationMs) {
        try {
            long ttlSeconds = expirationMs / 1000;
            if (ttlSeconds <= 0) {
                log.warn("Token already expired, skipping blacklist for jti={}", jti);
                return;
            }
            String key = BLACKLIST_PREFIX + jti;
            Map<String, String> data = Map.of(
                    "jti", jti,
                    "blacklistedAt", String.valueOf(System.currentTimeMillis())
            );
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), ttlSeconds, TimeUnit.SECONDS);
            log.info("Blacklisted token jti={} with TTL={}s", jti, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to blacklist token jti={}: {}", jti, e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
