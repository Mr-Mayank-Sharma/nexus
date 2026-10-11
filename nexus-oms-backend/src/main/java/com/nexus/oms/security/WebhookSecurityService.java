package com.nexus.oms.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class WebhookSecurityService {
    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityService.class);

    private final StringRedisTemplate redisTemplate;
    private final String shopifySecret;
    private final String bigCommerceSecret;

    public WebhookSecurityService(StringRedisTemplate redisTemplate,
                                   @Value("${app.webhook.shopify-secret:}") String shopifySecret,
                                   @Value("${app.webhook.bigcommerce-secret:}") String bigCommerceSecret) {
        this.redisTemplate = redisTemplate;
        this.shopifySecret = shopifySecret;
        this.bigCommerceSecret = bigCommerceSecret;
    }

    /**
     * Verify Shopify webhook HMAC-SHA256 signature.
     */
    public String globalShopifySecret() {
        return shopifySecret;
    }

    public boolean verifyShopifyHmac(String body, String signatureHeader) {
        return verifyShopifyHmac(body.getBytes(StandardCharsets.UTF_8), signatureHeader, List.of(shopifySecret));
    }

    /**
     * Verifies against the exact bytes Shopify signed, trying each candidate secret. Shopify signs
     * with the app's client secret, so the shop's own secret is tried first and the globally
     * configured one is a fallback for single-app installs. Blank secrets never match.
     */
    public boolean verifyShopifyHmac(byte[] body, String signatureHeader, Collection<String> secrets) {
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        boolean valid = false;
        for (String candidate : secrets) {
            if (candidate == null || candidate.isEmpty()) continue;
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(candidate.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                String computed = Base64.getEncoder().encodeToString(mac.doFinal(body));
                valid |= constantTimeEquals(computed, signatureHeader.trim());   // no early exit
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                log.error("HMAC verification failed", e);
            }
        }
        return valid;
    }

    /**
     * Verify BigCommerce webhook signature.
     */
    public boolean verifyBigCommerceHmac(String body, String signatureHeader) {
        if (bigCommerceSecret.isEmpty() || signatureHeader == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret = new SecretKeySpec(bigCommerceSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secret);
            byte[] hmac = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computed = java.util.HexFormat.of().formatHex(hmac);
            return constantTimeEquals(computed, signatureHeader.trim().toLowerCase());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC verification failed", e);
            return false;
        }
    }

    /**
     * Check for webhook replay attack - store event IDs in Redis with 24h TTL.
     * Returns true if the event has already been processed (duplicate).
     */
    public boolean isDuplicateEvent(String eventId) {
        // Atomic SET NX: two simultaneous deliveries of one event cannot both pass.
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent("webhook:processed:" + eventId, "1", 24, TimeUnit.HOURS);
        return Boolean.FALSE.equals(claimed);
    }

    /**
     * Forgets an event whose processing failed, so the sender's retry is processed instead of being
     * dropped as a duplicate. Only call this for events whose signature was verified.
     */
    public void releaseEvent(String eventId) {
        redisTemplate.delete("webhook:processed:" + eventId);
    }

    /**
     * Log webhook attempt for audit trail.
     */
    public void logWebhookAttempt(String source, String eventId, boolean valid, String details) {
        log.info("WEBHOOK: source={} eventId={} valid={} details={}", source, eventId, valid, details);
    }

    /** {@link MessageDigest#isEqual} does not short-circuit, so timing does not leak the signature. */
    static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
