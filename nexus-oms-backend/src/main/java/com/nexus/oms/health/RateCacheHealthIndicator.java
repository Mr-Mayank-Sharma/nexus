package com.nexus.oms.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RateCacheHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;

    public RateCacheHealthIndicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up().build();
            }
            return Health.unknown().withDetail("ping", pong).build();
        } catch (Exception e) {
            return Health.unknown().withDetail("reason", "Redis unavailable - rate cache degraded").build();
        }
    }
}
