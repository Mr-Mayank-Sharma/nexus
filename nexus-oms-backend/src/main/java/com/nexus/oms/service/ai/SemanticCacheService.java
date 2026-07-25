package com.nexus.oms.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis + PostgreSQL backed semantic cache for LLM prompts.
 * Checks Redis first (fast), falls back to DB (persistent), then stores in both.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);
    private static final String REDIS_PREFIX = "nexus:ai:cache:";
    private static final long TTL_SECONDS = 300; // 5 minutes

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public SemanticCacheService(
            StringRedisTemplate redisTemplate,
            JdbcTemplate jdbcTemplate,
            @Value("${nexus.ai.cache.enabled:false}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    /**
     * Look up a cached response for the given prompt + model combination.
     * Returns empty if not cached or expired.
     */
    public Optional<String> get(String prompt, String modelName) {
        if (!enabled) return Optional.empty();

        try {
            String hash = computeHash(prompt, modelName);
            String redisKey = REDIS_PREFIX + hash;

            // L1: Redis
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("Cache HIT (Redis) for hash={}", hash);
                // Update hit count in DB (async-ish, best effort)
                updateHitCount(hash);
                return Optional.of(cached);
            }

            // L2: PostgreSQL
            var rows = jdbcTemplate.queryForList(
                "SELECT response_text FROM ai_semantic_cache " +
                "WHERE prompt_hash = ? AND expires_at > NOW() LIMIT 1", hash);
            if (!rows.isEmpty()) {
                String dbResponse = (String) rows.get(0).get("response_text");
                // Promote to Redis
                redisTemplate.opsForValue().set(redisKey, dbResponse, TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("Cache HIT (DB->Redis) for hash={}", hash);
                updateHitCount(hash);
                return Optional.of(dbResponse);
            }

            log.debug("Cache MISS for hash={}", hash);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Cache lookup failed, treating as miss: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store a prompt-response pair in both Redis and PostgreSQL.
     */
    public void put(String prompt, String modelName, String response, UUID tenantId) {
        if (!enabled || response == null) return;

        try {
            String hash = computeHash(prompt, modelName);
            String redisKey = REDIS_PREFIX + hash;

            // L1: Redis
            redisTemplate.opsForValue().set(redisKey, response, TTL_SECONDS, TimeUnit.SECONDS);

            // L2: PostgreSQL
            jdbcTemplate.update(
                "INSERT INTO ai_semantic_cache (tenant_id, prompt_hash, prompt_text, response_text, model_name, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, prompt_hash) DO UPDATE SET " +
                "response_text = EXCLUDED.response_text, hit_count = ai_semantic_cache.hit_count + 1, " +
                "last_hit_at = NOW(), expires_at = EXCLUDED.expires_at",
                tenantId, hash, truncate(prompt, 2000), response, modelName,
                LocalDateTime.now().plusSeconds(TTL_SECONDS));

            log.debug("Cache STORE for hash={}", hash);
        } catch (Exception e) {
            log.warn("Cache store failed: {}", e.getMessage());
        }
    }

    /**
     * Compute SHA-256 hash of prompt + model for cache key.
     */
    String computeHash(String prompt, String modelName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = (modelName + ":" + prompt).toLowerCase().trim();
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void updateHitCount(String hash) {
        try {
            jdbcTemplate.update(
                "UPDATE ai_semantic_cache SET hit_count = hit_count + 1, last_hit_at = NOW() WHERE prompt_hash = ?", hash);
        } catch (Exception e) {
            log.warn("Failed to update hit count: {}", e.getMessage());
        }
    }

    /**
     * Evict expired cache entries (runs every 10 minutes).
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 60000)
    public void evictExpired() {
        if (!enabled) return;
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM ai_semantic_cache WHERE expires_at < NOW()");
            if (deleted > 0) {
                log.info("Evicted {} expired cache entries", deleted);
            }
        } catch (Exception e) {
            log.warn("Cache eviction failed: {}", e.getMessage());
        }
    }

    /**
     * Get cache statistics for monitoring.
     */
    public Map<String, Object> getStats() {
        try {
            var row = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) as entries, COALESCE(SUM(hit_count), 0) as total_hits, " +
                "COALESCE(SUM(cost_saved_usd), 0) as total_saved FROM ai_semantic_cache WHERE expires_at > NOW()");
            return Map.of(
                "entries", row.get("entries"),
                "totalHits", row.get("total_hits"),
                "estimatedSavingsUsd", row.get("total_saved"),
                "enabled", enabled
            );
        } catch (Exception e) {
            return Map.of("entries", 0, "enabled", enabled, "error", e.getMessage());
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
