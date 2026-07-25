package com.nexus.oms.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks OpenAI API costs per request, exposes Prometheus metrics.
 * Cost data persisted to ai_cost_tracking table for analytics.
 */
@Service
public class AiCostTrackingService {

    private static final Logger log = LoggerFactory.getLogger(AiCostTrackingService.class);

    // GPT-4o pricing (per 1K tokens) — update as OpenAI changes pricing
    private static final BigDecimal GPT4O_INPUT_PER_1K = new BigDecimal("0.002500");
    private static final BigDecimal GPT4O_OUTPUT_PER_1K = new BigDecimal("0.010000");
    private static final BigDecimal GPT4O_MINI_INPUT_PER_1K = new BigDecimal("0.000150");
    private static final BigDecimal GPT4O_MINI_OUTPUT_PER_1K = new BigDecimal("0.000600");

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public AiCostTrackingService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record cost for an OpenAI API call. Returns the computed cost in USD.
     */
    public BigDecimal recordCost(String modelName, String operationType, int inputTokens,
                                  int outputTokens, String requestId, String endpoint,
                                  int latencyMs, boolean cached, UUID tenantId) {
        BigDecimal cost = calculateCost(modelName, inputTokens, outputTokens);

        // Persist to DB
        try {
            jdbcTemplate.update(
                "INSERT INTO ai_cost_tracking " +
                "(tenant_id, model_name, operation_type, input_tokens, output_tokens, total_tokens, " +
                "cost_usd, request_id, endpoint, latency_ms, cached) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, modelName, operationType, inputTokens, outputTokens,
                inputTokens + outputTokens, cost.setScale(6, RoundingMode.HALF_UP),
                requestId, endpoint, latencyMs, cached);
        } catch (Exception e) {
            log.warn("Failed to persist cost tracking: {}", e.getMessage());
        }

        // Prometheus metrics
        meterRegistry.gauge("nexus.ai.cost.usd.total", cost.doubleValue());
        meterRegistry.counter("nexus.ai.tokens.total", "model", modelName, "type", "input")
            .increment(inputTokens);
        meterRegistry.counter("nexus.ai.tokens.total", "model", modelName, "type", "output")
            .increment(outputTokens);
        meterRegistry.counter("nexus.ai.requests.total", "model", modelName, "operation", operationType,
                "cached", String.valueOf(cached))
            .increment();
        meterRegistry.timer("nexus.ai.latency.ms", "model", modelName)
            .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        if (cached) {
            getOrCreateCounter("nexus.ai.cache.hits").increment();
        } else {
            getOrCreateCounter("nexus.ai.cache.misses").increment();
        }

        log.debug("Cost recorded: model={}, tokens={}/{}, cost=${}, cached={}",
                modelName, inputTokens, outputTokens, cost.toPlainString(), cached);

        return cost;
    }

    /**
     * Calculate cost based on model and token counts.
     */
    public BigDecimal calculateCost(String modelName, int inputTokens, int outputTokens) {
        BigDecimal inputRate;
        BigDecimal outputRate;

        if (modelName != null && modelName.contains("mini")) {
            inputRate = GPT4O_MINI_INPUT_PER_1K;
            outputRate = GPT4O_MINI_OUTPUT_PER_1K;
        } else {
            inputRate = GPT4O_INPUT_PER_1K;
            outputRate = GPT4O_OUTPUT_PER_1K;
        }

        BigDecimal inputCost = new BigDecimal(inputTokens).divide(new BigDecimal(1000))
            .multiply(inputRate);
        BigDecimal outputCost = new BigDecimal(outputTokens).divide(new BigDecimal(1000))
            .multiply(outputRate);

        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Get cost summary for monitoring dashboards.
     */
    public Map<String, Object> getCostSummary(UUID tenantId, int days) {
        try {
            var row = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(cost_usd), 0) as total_cost, " +
                "COALESCE(SUM(total_tokens), 0) as total_tokens, " +
                "COUNT(*) as total_requests, " +
                "COALESCE(SUM(CASE WHEN cached THEN 1 ELSE 0 END), 0) as cache_hits " +
                "FROM ai_cost_tracking WHERE tenant_id = ? AND created_at > NOW() - INTERVAL '" + days + " days'",
                tenantId);

            return Map.of(
                "totalCostUsd", row.get("total_cost"),
                "totalTokens", row.get("total_tokens"),
                "totalRequests", row.get("total_requests"),
                "cacheHits", row.get("cache_hits"),
                "periodDays", days
            );
        } catch (Exception e) {
            log.warn("Failed to get cost summary: {}", e.getMessage());
            return Map.of("totalCostUsd", 0, "totalTokens", 0, "totalRequests", 0);
        }
    }

    /**
     * Get cost breakdown by model.
     */
    public Map<String, Object> getCostByModel(UUID tenantId, int days) {
        try {
            var rows = jdbcTemplate.queryForList(
                "SELECT model_name, SUM(cost_usd) as cost, SUM(total_tokens) as tokens, COUNT(*) as requests " +
                "FROM ai_cost_tracking WHERE tenant_id = ? AND created_at > NOW() - INTERVAL '" + days + " days' " +
                "GROUP BY model_name ORDER BY cost DESC",
                tenantId);

            var breakdown = new java.util.LinkedHashMap<String, Object>();
            for (var row : rows) {
                breakdown.put((String) row.get("model_name"), Map.of(
                    "costUsd", row.get("cost"),
                    "tokens", row.get("tokens"),
                    "requests", row.get("requests")
                ));
            }
            return breakdown;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Counter getOrCreateCounter(String name) {
        return counters.computeIfAbsent(name, n -> Counter.builder(n).register(meterRegistry));
    }
}
