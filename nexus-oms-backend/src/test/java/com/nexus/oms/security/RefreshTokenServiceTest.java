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

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        refreshTokenService = new RefreshTokenService(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createRefreshToken_storesInRedisWithCorrectKeyFormat() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = refreshTokenService.createRefreshToken(userId, tenantId);

        assertNotNull(token);
        verify(valueOperations).set(
                argThat(key -> key.equals("refresh:" + token)),
                anyString(),
                eq(604800L),
                eq(TimeUnit.SECONDS)
        );
        verify(valueOperations).set(
                argThat(key -> key.equals("refresh:" + userId + ":" + token)),
                eq(token),
                eq(604800L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void createRefreshToken_returnsUniqueTokenEachCall() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token1 = refreshTokenService.createRefreshToken(userId, tenantId);
        String token2 = refreshTokenService.createRefreshToken(userId, tenantId);

        assertNotEquals(token1, token2);
    }

    @Test
    void validateRefreshToken_returnsDataForValidToken() throws Exception {
        String token = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Map<String, String> data = Map.of(
                "userId", userId.toString(),
                "tenantId", tenantId.toString(),
                "createdAt", Instant.now().toString(),
                "expiresAt", Instant.now().plusSeconds(3600).toString()
        );
        when(valueOperations.get("refresh:" + token)).thenReturn(objectMapper.writeValueAsString(data));

        Map<String, String> result = refreshTokenService.validateRefreshToken(token);

        assertNotNull(result);
        assertEquals(userId.toString(), result.get("userId"));
        assertEquals(tenantId.toString(), result.get("tenantId"));
    }

    @Test
    void validateRefreshToken_throwsForNonExistentToken() {
        String token = UUID.randomUUID().toString();
        when(valueOperations.get("refresh:" + token)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> refreshTokenService.validateRefreshToken(token));
        assertEquals("Refresh token not found or expired", ex.getMessage());
    }

    @Test
    void revokeRefreshToken_deletesFromRedis() throws Exception {
        String token = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        Map<String, String> data = Map.of(
                "userId", userId.toString(),
                "createdAt", Instant.now().toString(),
                "expiresAt", Instant.now().plusSeconds(3600).toString()
        );
        when(valueOperations.get("refresh:" + token)).thenReturn(objectMapper.writeValueAsString(data));
        when(redisTemplate.delete(anyString())).thenReturn(true);

        refreshTokenService.revokeRefreshToken(token);

        verify(redisTemplate).delete("refresh:" + token);
        verify(redisTemplate).delete("refresh:" + userId + ":" + token);
    }

    @Test
    void revokeRefreshToken_deletesTokenKeyEvenWhenDataNotFound() {
        String token = UUID.randomUUID().toString();
        when(valueOperations.get("refresh:" + token)).thenReturn(null);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        refreshTokenService.revokeRefreshToken(token);

        verify(redisTemplate).delete("refresh:" + token);
    }

    @Test
    void revokeAllUserTokens_scansWithCorrectPattern() {
        UUID userId = UUID.randomUUID();
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();

        String userKey1 = "refresh:" + userId + ":" + token1;
        String userKey2 = "refresh:" + userId + ":" + token2;
        when(redisTemplate.keys("refresh:" + userId + ":*")).thenReturn(Set.of(userKey1, userKey2));
        when(valueOperations.get(userKey1)).thenReturn(token1);
        when(valueOperations.get(userKey2)).thenReturn(token2);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        refreshTokenService.revokeAllUserTokens(userId);

        verify(redisTemplate).keys("refresh:" + userId + ":*");
        verify(redisTemplate).delete("refresh:" + token1);
        verify(redisTemplate).delete("refresh:" + token2);
        verify(redisTemplate).delete(userKey1);
        verify(redisTemplate).delete(userKey2);
    }

    @Test
    void revokeAllUserTokens_doesNothingWhenNoKeysExist() {
        UUID userId = UUID.randomUUID();
        when(redisTemplate.keys("refresh:" + userId + ":*")).thenReturn(null);

        refreshTokenService.revokeAllUserTokens(userId);

        verify(redisTemplate).keys("refresh:" + userId + ":*");
        verify(redisTemplate, never()).delete(anyString());
    }
}
