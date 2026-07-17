package com.nexus.oms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    void cacheManager_ReturnsNonNullRedisCacheManager() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();

        CacheManager cacheManager = redisConfig.cacheManager(connectionFactory, objectMapper);

        assertNotNull(cacheManager);
        assertInstanceOf(org.springframework.data.redis.cache.RedisCacheManager.class, cacheManager);
    }

    @Test
    void redisTemplate_ReturnsNonNullRedisTemplate() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();

        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory, objectMapper);

        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
    }
}
