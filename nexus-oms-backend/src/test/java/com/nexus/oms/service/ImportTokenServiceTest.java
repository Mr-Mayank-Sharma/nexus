package com.nexus.oms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImportTokenServiceTest {

    private ImportTokenService importTokenService;

    @BeforeEach
    void setUp() {
        importTokenService = new ImportTokenService("test-secret-key-for-hmac", 5000);
    }

    @Test
    void generateAndValidateToken() {
        String token = importTokenService.generateToken("PRODUCT");

        assertNotNull(token);
        assertTrue(token.contains("."));

        ImportTokenService.ImportTokenPayload payload = importTokenService.validateToken(token);

        assertNotNull(payload);
        assertEquals("PRODUCT", payload.entityType());
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
        ImportTokenService shortTtl = new ImportTokenService("test-secret-key-for-hmac", 1);
        String token = shortTtl.generateToken("PRODUCT");

        Thread.sleep(2);

        assertNull(shortTtl.validateToken(token));
    }

    @Test
    void getTtlMs() {
        assertEquals(5000, importTokenService.getTtlMs());
    }
}
