package com.nexus.oms.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
    public boolean verifyShopifyHmac(String body, String signatureHeader) {
        if (shopifySecret.isEmpty() || signatureHeader == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret = new SecretKeySpec(shopifySecret.getBytes(), "HmacSHA256");
            mac.init(secret);
            byte[] hmac = mac.doFinal(body.getBytes());
            String computed = Base64.getEncoder().encodeToString(hmac);
            return computed.equals(signatureHeader);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC verification failed", e);
            return false;
        }
    }

    /**
     * Verify BigCommerce webhook signature.
     */
    public boolean verifyBigCommerceHmac(String body, String signatureHeader) {
        if (bigCommerceSecret.isEmpty() || signatureHeader == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret = new SecretKeySpec(bigCommerceSecret.getBytes(), "HmacSHA256");
            mac.init(secret);
            byte[] hmac = mac.doFinal(body.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hmac) sb.append(String.format("%02x", b));
            return sb.toString().equals(signatureHeader);
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
        String key = "webhook:processed:" + eventId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) return true;
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
        return false;
    }

    /**
     * Log webhook attempt for audit trail.
     */
    public void logWebhookAttempt(String source, String eventId, boolean valid, String details) {
        log.info("WEBHOOK: source={} eventId={} valid={} details={}", source, eventId, valid, details);
    }
}
