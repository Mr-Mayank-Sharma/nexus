package com.nexus.oms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotent:";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CONFLICT = "CONFLICT";
    private static final String DELIMITER = "::";

    private final StringRedisTemplate redisTemplate;
    private final long lockTimeoutSeconds;
    private final long ttlHours;

    public IdempotencyService(StringRedisTemplate redisTemplate,
                              @Value("${idempotency.lock-timeout-seconds:10}") long lockTimeoutSeconds,
                              @Value("${idempotency.ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.lockTimeoutSeconds = lockTimeoutSeconds;
        this.ttlHours = ttlHours;
    }

    public String hashKey(String idempotencyKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return KEY_PREFIX + idempotencyKey.hashCode();
        }
    }

    public boolean tryAcquire(String redisKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, STATUS_PENDING);
        if (Boolean.TRUE.equals(acquired)) {
            redisTemplate.expire(redisKey, lockTimeoutSeconds, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    public String getStatus(String redisKey) {
        return redisTemplate.opsForValue().get(redisKey);
    }

    public CachedResponse getCompleted(String redisKey) {
        String val = redisTemplate.opsForValue().get(redisKey);
        if (val == null || !val.startsWith(STATUS_COMPLETED + DELIMITER)) {
            return null;
        }
        String[] parts = val.split(DELIMITER, 3);
        if (parts.length < 3) return null;
        return new CachedResponse(Integer.parseInt(parts[1]), parts[2]);
    }

    public void complete(String redisKey, int statusCode, String responseBody) {
        String val = STATUS_COMPLETED + DELIMITER + statusCode + DELIMITER + responseBody;
        redisTemplate.opsForValue().set(redisKey, val, ttlHours, TimeUnit.HOURS);
    }

    public void markConflict(String redisKey) {
        redisTemplate.opsForValue().set(redisKey, STATUS_CONFLICT, ttlHours, TimeUnit.HOURS);
    }

    public void delete(String redisKey) {
        redisTemplate.delete(redisKey);
    }

    public record CachedResponse(int statusCode, String body) {}
}
