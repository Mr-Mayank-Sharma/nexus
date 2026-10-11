package com.nexus.oms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookSecurityServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String TEST_SECRET = "test-webhook-secret-12345";

    private WebhookSecurityService webhookSecurityService;

    @BeforeEach
    void setUp() {
        webhookSecurityService = new WebhookSecurityService(redisTemplate, TEST_SECRET, TEST_SECRET);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void verifyShopifyHmac_returnsTrueForValidSignature() throws Exception {
        String body = "{\"order_id\":12345}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(), "HmacSHA256"));
        String validSignature = Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes()));

        assertTrue(webhookSecurityService.verifyShopifyHmac(body, validSignature));
    }

    @Test
    void verifyShopifyHmac_returnsFalseForInvalidSignature() {
        String body = "{\"order_id\":12345}";

        assertFalse(webhookSecurityService.verifyShopifyHmac(body, "invalid-signature-base64"));
    }

    @Test
    void verifyShopifyHmac_returnsFalseWhenSecretIsEmpty() {
        WebhookSecurityService serviceWithEmptySecret = new WebhookSecurityService(redisTemplate, "", "");
        assertFalse(serviceWithEmptySecret.verifyShopifyHmac("body", "signature"));
    }

    @Test
    void verifyShopifyHmac_returnsFalseWhenSignatureIsNull() {
        assertFalse(webhookSecurityService.verifyShopifyHmac("body", null));
    }

    @Test
    void verifyShopifyHmac_returnsFalseForDifferentBody() throws Exception {
        String body = "{\"order_id\":12345}";
        String differentBody = "{\"order_id\":99999}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes()));

        assertFalse(webhookSecurityService.verifyShopifyHmac(differentBody, signature));
    }

    @Test
    void isDuplicateEvent_claimsNewEventAtomically() {
        when(valueOperations.setIfAbsent("webhook:processed:evt-001", "1", 24, TimeUnit.HOURS)).thenReturn(true);

        assertFalse(webhookSecurityService.isDuplicateEvent("evt-001"));
    }

    @Test
    void isDuplicateEvent_returnsTrueWhenAlreadyClaimed() {
        when(valueOperations.setIfAbsent("webhook:processed:evt-002", "1", 24, TimeUnit.HOURS)).thenReturn(false);

        assertTrue(webhookSecurityService.isDuplicateEvent("evt-002"));
    }

    @Test
    void releaseEvent_forgetsTheClaim_soTheRetryIsProcessed() {
        webhookSecurityService.releaseEvent("evt-003");

        verify(redisTemplate).delete("webhook:processed:evt-003");
    }

    @Test
    void shopifyHmac_acceptsTheShopsOwnSecret_andRejectsOthers() throws Exception {
        byte[] body = "{\"id\":1}\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);   // trailing CRLF matters
        String sig = sign(body, "shop-secret");

        assertTrue(webhookSecurityService.verifyShopifyHmac(body, sig, java.util.List.of("shop-secret", TEST_SECRET)));
        assertTrue(webhookSecurityService.verifyShopifyHmac(body, sig, java.util.List.of(TEST_SECRET, "shop-secret")));
        assertFalse(webhookSecurityService.verifyShopifyHmac(body, sig, java.util.List.of(TEST_SECRET, "")));
        assertFalse(webhookSecurityService.verifyShopifyHmac(body, sig, java.util.List.of("")), "blank secrets never match");
        assertFalse(webhookSecurityService.verifyShopifyHmac(body, null, java.util.List.of("shop-secret")));
    }

    @Test
    void shopifyHmac_isComputedOverTheExactBytes() throws Exception {
        byte[] original = "{\"a\":1}\n{\"b\":2}\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sig = sign(original, TEST_SECRET);
        byte[] rejoinedLines = "{\"a\":1}\n{\"b\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);   // what line-joining produced

        assertTrue(webhookSecurityService.verifyShopifyHmac(original, sig, java.util.List.of(TEST_SECRET)));
        assertFalse(webhookSecurityService.verifyShopifyHmac(rejoinedLines, sig, java.util.List.of(TEST_SECRET)));
    }

    static String sign(byte[] body, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
