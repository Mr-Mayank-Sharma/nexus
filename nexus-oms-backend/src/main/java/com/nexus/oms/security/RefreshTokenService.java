package com.nexus.oms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String REFRESH_PREFIX = "refresh:";
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;
    private static final long REFRESH_TOKEN_TTL_SECONDS = REFRESH_TOKEN_TTL_DAYS * 24 * 60 * 60; // 604800s

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RefreshTokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String createRefreshToken(UUID userId, UUID tenantId) {
        String token = UUID.randomUUID().toString();
        try {
            Map<String, String> data = Map.of(
                    "userId", userId.toString(),
                    "tenantId", tenantId.toString(),
                    "createdAt", Instant.now().toString(),
                    "expiresAt", Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS).toString()
            );

            // Store with both token key and user-scannable key
            String tokenKey = REFRESH_PREFIX + token;
            String userKey = REFRESH_PREFIX + userId + ":" + token;

            String jsonValue = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(tokenKey, jsonValue, REFRESH_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(userKey, token, REFRESH_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

            log.info("Created refresh token for userId={}, tenantId={}", userId, tenantId);
            return token;
        } catch (Exception e) {
            log.error("Failed to create refresh token for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to create refresh token", e);
        }
    }

    public Map<String, String> validateRefreshToken(String token) {
        String key = REFRESH_PREFIX + token;
        String jsonValue = redisTemplate.opsForValue().get(key);

        if (jsonValue == null) {
            throw new RuntimeException("Refresh token not found or expired");
        }

        try {
            Map<String, String> data = objectMapper.readValue(jsonValue, Map.class);

            Instant expiresAt = Instant.parse(data.get("expiresAt"));
            if (Instant.now().isAfter(expiresAt)) {
                revokeRefreshToken(token);
                throw new RuntimeException("Refresh token expired");
            }

            return data;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse refresh token data: {}", e.getMessage());
            throw new RuntimeException("Invalid refresh token data", e);
        }
    }

    public void revokeRefreshToken(String token) {
        String tokenKey = REFRESH_PREFIX + token;
        String jsonValue = redisTemplate.opsForValue().get(tokenKey);

        if (jsonValue != null) {
            try {
                Map<String, String> data = objectMapper.readValue(jsonValue, Map.class);
                String userId = data.get("userId");
                String userKey = REFRESH_PREFIX + userId + ":" + token;
                redisTemplate.delete(userKey);
            } catch (Exception e) {
                log.error("Failed to clean up user-scannable key: {}", e.getMessage());
            }
        }

        redisTemplate.delete(tokenKey);
        log.info("Revoked refresh token={}", token);
    }

    public void revokeAllUserTokens(UUID userId) {
        String pattern = REFRESH_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            for (String userKey : keys) {
                try {
                    // Extract token value from the user key and delete both sides
                    String tokenValue = redisTemplate.opsForValue().get(userKey);
                    if (tokenValue != null) {
                        redisTemplate.delete(REFRESH_PREFIX + tokenValue);
                    }
                    redisTemplate.delete(userKey);
                } catch (Exception e) {
                    log.error("Failed to revoke key {}: {}", userKey, e.getMessage());
                }
            }
            log.info("Revoked {} refresh tokens for userId={}", keys.size(), userId);
        }
    }
}
