package com.nexus.oms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate, 10, 24);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void hashKey_producesDeterministicResult() {
        String key1 = idempotencyService.hashKey("my-idempotent-key");
        String key2 = idempotencyService.hashKey("my-idempotent-key");
        assertEquals(key1, key2);
        assertTrue(key1.startsWith("idempotent:"));
    }

    @Test
    void tryAcquire_whenNotExists_returnsTrueAndSetsExpiry() {
        when(valueOperations.setIfAbsent(anyString(), eq(IdempotencyService.STATUS_PENDING)))
                .thenReturn(true);

        boolean result = idempotencyService.tryAcquire("idempotent:abc123");

        assertTrue(result);
        verify(valueOperations).setIfAbsent("idempotent:abc123", "PENDING");
        verify(redisTemplate).expire("idempotent:abc123", 10, TimeUnit.SECONDS);
    }

    @Test
    void tryAcquire_whenExists_returnsFalse() {
        when(valueOperations.setIfAbsent(anyString(), eq(IdempotencyService.STATUS_PENDING)))
                .thenReturn(false);

        boolean result = idempotencyService.tryAcquire("idempotent:abc123");

        assertFalse(result);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void getCompleted_whenCompleted_returnsCachedResponse() {
        when(valueOperations.get("idempotent:abc"))
                .thenReturn("COMPLETED::200::{\"id\":\"order-1\"}");

        IdempotencyService.CachedResponse result = idempotencyService.getCompleted("idempotent:abc");

        assertNotNull(result);
        assertEquals(200, result.statusCode());
        assertEquals("{\"id\":\"order-1\"}", result.body());
    }

    @Test
    void getCompleted_whenPending_returnsNull() {
        when(valueOperations.get("idempotent:abc")).thenReturn("PENDING");

        IdempotencyService.CachedResponse result = idempotencyService.getCompleted("idempotent:abc");

        assertNull(result);
    }

    @Test
    void getCompleted_whenMissing_returnsNull() {
        when(valueOperations.get("idempotent:abc")).thenReturn(null);

        IdempotencyService.CachedResponse result = idempotencyService.getCompleted("idempotent:abc");

        assertNull(result);
    }

    @Test
    void complete_setsValueWithTtl() {
        idempotencyService.complete("idempotent:abc", 201, "{\"status\":\"created\"}");

        verify(valueOperations).set(
                eq("idempotent:abc"),
                eq("COMPLETED::201::{\"status\":\"created\"}"),
                eq(24L),
                eq(TimeUnit.HOURS));
    }

    @Test
    void markConflict_setsConflictStatus() {
        idempotencyService.markConflict("idempotent:abc");

        verify(valueOperations).set(
                eq("idempotent:abc"),
                eq(IdempotencyService.STATUS_CONFLICT),
                eq(24L),
                eq(TimeUnit.HOURS));
    }

    @Test
    void delete_removesKey() {
        idempotencyService.delete("idempotent:abc");

        verify(redisTemplate).delete("idempotent:abc");
    }

    @Test
    void getStatus_returnsCurrentValue() {
        when(valueOperations.get("idempotent:abc")).thenReturn("COMPLETED::200::body");

        String status = idempotencyService.getStatus("idempotent:abc");

        assertEquals("COMPLETED::200::body", status);
    }
}
