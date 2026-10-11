package com.nexus.oms.filter;

import com.nexus.oms.security.WebhookSecurityService;
import com.nexus.oms.service.IntegrationStoreService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookAuthenticationFilterTest {

    private static final String GLOBAL = "global-secret";
    private static final String SHOP_SECRET = "this-shops-app-secret";
    private static final String BODY = "{\"id\":42}\r\n";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private WebhookAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(stores.findWebhookSecretForDomain("acme.myshopify.com")).thenReturn(Optional.of(SHOP_SECRET));
        filter = new WebhookAuthenticationFilter(new WebhookSecurityService(redis, GLOBAL, ""), stores);
    }

    private MockHttpServletRequest shopifyRequest(String signedWith, String eventId) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/webhooks/shopify/orders/create");
        req.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        req.addHeader("X-Shopify-Shop-Domain", "acme.myshopify.com");
        if (eventId != null) req.addHeader("X-Shopify-Webhook-Id", eventId);
        if (signedWith != null) {
            req.addHeader("X-Shopify-Hmac-Sha256", sign(BODY.getBytes(StandardCharsets.UTF_8), signedWith));
        }
        return req;
    }

    private static String sign(byte[] body, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(body));
    }

    @Test
    void forgedRequest_isRejected_andNeverTouchesTheEventLedger() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(shopifyRequest("attacker-guess", "evt-1"), res, chain);

        assertEquals(401, res.getStatus());
        verifyNoInteractions(chain);
        verify(ops, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void missingEventId_isRejected() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(shopifyRequest(SHOP_SECRET, null), res, chain);

        assertEquals(401, res.getStatus());
    }

    @Test
    void validSignatureFromTheShopsOwnSecret_isForwarded_withTheBodyIntact() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(shopifyRequest(SHOP_SECRET, "evt-2"), res, chain);

        verify(chain).doFilter(any(), any());
        verify(ops).setIfAbsent("webhook:processed:evt-2", "1", 24, TimeUnit.HOURS);
    }

    @Test
    void globalSecretStillWorks_forSingleAppInstalls() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(shopifyRequest(GLOBAL, "evt-3"), res, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void redelivery_isAcknowledged_butNotProcessedTwice() throws Exception {
        when(ops.setIfAbsent(eq("webhook:processed:evt-4"), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(shopifyRequest(SHOP_SECRET, "evt-4"), res, chain);

        assertEquals(200, res.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void failedProcessing_releasesTheClaim_soShopifysRetryIsNotDropped() throws Exception {
        doThrow(new IllegalStateException("db down")).when(chain).doFilter(any(), any());

        assertThrows(IllegalStateException.class,
                () -> filter.doFilter(shopifyRequest(SHOP_SECRET, "evt-5"), new MockHttpServletResponse(), chain));

        verify(redis).delete("webhook:processed:evt-5");
    }

    @Test
    void serverErrorResponse_releasesTheClaim() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        doAnswer(inv -> { ((MockHttpServletResponse) inv.getArgument(1)).setStatus(503); return null; })
                .when(chain).doFilter(any(), any());

        filter.doFilter(shopifyRequest(SHOP_SECRET, "evt-6"), res, chain);

        verify(redis).delete("webhook:processed:evt-6");
    }

    @Test
    void unrelatedPaths_passThroughUntouched() throws ServletException, java.io.IOException {
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/orders"), res, chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(redis);
    }
}
