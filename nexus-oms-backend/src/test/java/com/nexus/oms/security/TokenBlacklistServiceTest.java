package com.nexus.oms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tokenBlacklistService = new TokenBlacklistService(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void blacklist_storesWithCorrectKey() {
        String jti = "test-jti-123";
        long expirationMs = 3600000L;

        tokenBlacklistService.blacklist(jti, expirationMs);

        verify(valueOperations).set(
                eq("blacklist:" + jti),
                argThat(json -> json.contains("test-jti-123")),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void blacklist_skipsWhenTokenAlreadyExpired() {
        String jti = "expired-jti";
        long expirationMs = 0L;

        tokenBlacklistService.blacklist(jti, expirationMs);

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void blacklist_skipsWhenExpirationIsNegative() {
        String jti = "expired-jti";
        long expirationMs = -1000L;

        tokenBlacklistService.blacklist(jti, expirationMs);

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void isBlacklisted_returnsTrueForBlacklistedToken() {
        String jti = "blacklisted-jti";
        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(jti));
    }

    @Test
    void isBlacklisted_returnsFalseForNonBlacklistedToken() {
        String jti = "clean-jti";
        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(false);

        assertFalse(tokenBlacklistService.isBlacklisted(jti));
    }

    @Test
    void isBlacklisted_returnsFalseWhenRedisReturnsNull() {
        String jti = "unknown-jti";
        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(null);

        assertFalse(tokenBlacklistService.isBlacklisted(jti));
    }
}
