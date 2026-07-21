package com.nexus.oms.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateCacheHealthIndicatorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    private RateCacheHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        lenient().when(connectionFactory.getConnection()).thenReturn(connection);
        indicator = new RateCacheHealthIndicator(redisTemplate);
    }

    @Test
    void health_whenRedisPongs_returnsUp() {
        when(connection.ping()).thenReturn("PONG");

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void health_whenRedisReturnsUnexpected_returnsUnknown() {
        when(connection.ping()).thenReturn("UNKNOWN");

        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
    }

    @Test
    void health_whenRedisThrows_returnsUnknown() {
        when(connection.ping()).thenThrow(new RuntimeException("Redis unavailable"));

        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
    }
}
