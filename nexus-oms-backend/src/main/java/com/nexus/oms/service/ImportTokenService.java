package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class ImportTokenService {

    private static final Logger log = LoggerFactory.getLogger(ImportTokenService.class);
    private static final long DEFAULT_TTL_MS = 30 * 60 * 1000;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec signingKey;
    private final ObjectMapper objectMapper;
    private final long ttlMs;

    public ImportTokenService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.import-token.ttl-ms:1800000}") long ttlMs) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
        this.objectMapper = new ObjectMapper();
        this.ttlMs = ttlMs;
    }

    public String generateToken(String entityType) {
        long now = System.currentTimeMillis();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "entityType", entityType,
                "iat", now,
                "exp", now + ttlMs
            ));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.getBytes(StandardCharsets.UTF_8));
            String signature = sign(encodedPayload);
            return encodedPayload + "." + signature;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to generate import token", e);
        }
    }

    public ImportTokenPayload validateToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 2) return null;

        String encodedPayload = parts[0];
        String signature = parts[1];

        String expectedSignature = sign(encodedPayload);
        if (!constantTimeEquals(signature, expectedSignature)) return null;

        try {
            String json = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            Map<?, ?> data = objectMapper.readValue(json, Map.class);
            long exp = ((Number) data.get("exp")).longValue();
            if (System.currentTimeMillis() > exp) return null;
            return new ImportTokenPayload((String) data.get("entityType"), exp);
        } catch (Exception e) {
            log.warn("Failed to parse import token payload: {}", e.getMessage());
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] sigBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign import token", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    public record ImportTokenPayload(String entityType, long exp) {}
}
