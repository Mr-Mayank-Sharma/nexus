package com.nexus.oms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private ImportTokenService importTokenService;

    @BeforeEach
    void setUp() {
        importTokenService = new ImportTokenService("test-secret-key-for-hmac", 5000, redisTemplate);
    }

    @Test
    void generateAndValidateToken() {
        String token = importTokenService.generateToken("PRODUCT");

        assertNotNull(token);
        assertTrue(token.contains("."));

        ImportTokenService.ImportTokenPayload payload = importTokenService.validateToken(token);

        assertNotNull(payload);
        assertEquals("PRODUCT", payload.entityType());
        assertNotNull(payload.jti());
    }

    @Test
    void validateToken_null_returnsNull() {
        assertNull(importTokenService.validateToken(null));
    }

    @Test
    void validateToken_empty_returnsNull() {
        assertNull(importTokenService.validateToken(""));
    }

    @Test
    void validateToken_invalidFormat_returnsNull() {
        assertNull(importTokenService.validateToken("invalid-token"));
    }

    @Test
    void validateToken_badSignature_returnsNull() {
        String token = importTokenService.generateToken("PRODUCT");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".invalidsignature";

        assertNull(importTokenService.validateToken(tampered));
    }

    @Test
    void validateToken_expired_returnsNull() throws Exception {
        ImportTokenService shortTtl = new ImportTokenService("test-secret-key-for-hmac", 1, redisTemplate);
        String token = shortTtl.generateToken("PRODUCT");

        Thread.sleep(2);

        assertNull(shortTtl.validateToken(token));
    }

    @Test
    void getTtlMs() {
        assertEquals(5000, importTokenService.getTtlMs());
    }
}
