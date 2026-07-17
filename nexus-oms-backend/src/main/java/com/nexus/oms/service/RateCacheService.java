package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.RateShoppingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RateCacheService {

    private static final Logger log = LoggerFactory.getLogger(RateCacheService.class);
    private static final String CACHE_KEY_PREFIX = "rate:";
    private static final String STATS_HITS = "ratecache:hits";
    private static final String STATS_MISSES = "ratecache:misses";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration defaultTtl;

    public RateCacheService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            @Value("${rate-cache.ttl-minutes:15}") int ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.defaultTtl = Duration.ofMinutes(ttlMinutes);
    }

    public String buildCacheKey(String originZip, String destZip, double weightKg,
                                 String carrierCode, String serviceType) {
        String raw = originZip + "|" + destZip + "|" + weightKg + "|"
                + (carrierCode != null ? carrierCode : "") + "|"
                + (serviceType != null ? serviceType : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return CACHE_KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return CACHE_KEY_PREFIX + raw.hashCode();
        }
    }

    public RateShoppingResult get(String cacheKey) {
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json == null) {
            redisTemplate.opsForValue().increment(STATS_MISSES);
            return null;
        }
        try {
            redisTemplate.opsForValue().increment(STATS_HITS);
            return objectMapper.readValue(json, RateShoppingResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached rate: {}", e.getMessage());
            redisTemplate.delete(cacheKey);
            return null;
        }
    }

    public void put(String cacheKey, RateShoppingResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, defaultTtl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize rate result for cache: {}", e.getMessage());
        }
    }

    public void put(String cacheKey, RateShoppingResult result, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize rate result for cache: {}", e.getMessage());
        }
    }

    public void evict(String cacheKey) {
        redisTemplate.delete(cacheKey);
    }

    public Map<String, Object> getStats() {
        String hitsStr = redisTemplate.opsForValue().get(STATS_HITS);
        String missesStr = redisTemplate.opsForValue().get(STATS_MISSES);
        long hits = hitsStr != null ? Long.parseLong(hitsStr) : 0;
        long misses = missesStr != null ? Long.parseLong(missesStr) : 0;
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100.0 : 0.0;

        int size = Optional.ofNullable(redisTemplate.keys(CACHE_KEY_PREFIX + "*"))
                .map(Set::size)
                .orElse(0);

        return Map.of(
                "hits", hits,
                "misses", misses,
                "totalRequests", total,
                "hitRate", String.format("%.1f%%", hitRate),
                "cachedEntries", size,
                "ttlMinutes", defaultTtl.toMinutes()
        );
    }
}
