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
    void isDuplicateEvent_returnsFalseForNewEvent() {
        String eventId = "evt-001";
        when(redisTemplate.hasKey("webhook:processed:" + eventId)).thenReturn(false);

        assertFalse(webhookSecurityService.isDuplicateEvent(eventId));

        verify(valueOperations).set("webhook:processed:" + eventId, "1", 24, TimeUnit.HOURS);
    }

    @Test
    void isDuplicateEvent_returnsTrueForDuplicate() {
        String eventId = "evt-002";
        when(redisTemplate.hasKey("webhook:processed:" + eventId)).thenReturn(true);

        assertTrue(webhookSecurityService.isDuplicateEvent(eventId));

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void isDuplicateEvent_returnsFalseWhenRedisReturnsNull() {
        String eventId = "evt-003";
        when(redisTemplate.hasKey("webhook:processed:" + eventId)).thenReturn(null);

        assertFalse(webhookSecurityService.isDuplicateEvent(eventId));
    }
}
