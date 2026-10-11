package com.nexus.oms.filter;

import com.nexus.oms.security.WebhookSecurityService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
public class WebhookAuthenticationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(WebhookAuthenticationFilter.class);

    private final WebhookSecurityService webhookSecurityService;
    private final com.nexus.oms.service.IntegrationStoreService storeService;

    public WebhookAuthenticationFilter(WebhookSecurityService webhookSecurityService,
                                       com.nexus.oms.service.IntegrationStoreService storeService) {
        this.webhookSecurityService = webhookSecurityService;
        this.storeService = storeService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        boolean shopify = path.contains("/webhooks/") && path.contains("shopify");
        boolean bigCommerce = path.contains("/webhooks/") && path.contains("bigcommerce");
        if (!(shopify || bigCommerce) || path.endsWith("/register")) {
            chain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(req);
        byte[] body = wrapped.getCachedBody();
        String source = shopify ? "shopify" : "bigcommerce";
        String eventId = firstNonNull(wrapped.getHeader("X-Shopify-Webhook-Id"),
                wrapped.getHeader("X-Bc-Webhook-Id"), wrapped.getHeader("X-Event-Id"));

        // 1. Authenticate first. Nothing below is allowed to change state for an unsigned request:
        //    recording an event id before the signature check let anyone who learned an id burn it,
        //    so the genuine delivery was later dropped as a "duplicate".
        boolean valid = false;
        if (eventId != null) {
            valid = shopify
                    ? webhookSecurityService.verifyShopifyHmac(body, wrapped.getHeader("X-Shopify-Hmac-Sha256"),
                            shopifySecrets(wrapped.getHeader("X-Shopify-Shop-Domain")))
                    : webhookSecurityService.verifyBigCommerceHmac(new String(body, StandardCharsets.UTF_8),
                            wrapped.getHeader("X-Bc-Webhook-Signature"));
        }
        if (!valid) {
            webhookSecurityService.logWebhookAttempt(source, eventId, false, "invalid signature");
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid webhook signature\"}");
            return;
        }

        // 2. Authenticated: claim the event atomically so a concurrent redelivery is skipped.
        if (webhookSecurityService.isDuplicateEvent(eventId)) {
            webhookSecurityService.logWebhookAttempt(source, eventId, true, "duplicate ignored");
            res.setStatus(200);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":\"ok\",\"message\":\"duplicate event ignored\"}");
            return;
        }
        webhookSecurityService.logWebhookAttempt(source, eventId, true, "accepted");

        // 3. If processing fails, release the claim so the sender's retry is not discarded.
        try {
            chain.doFilter(wrapped, response);
        } catch (IOException | ServletException | RuntimeException e) {
            webhookSecurityService.releaseEvent(eventId);
            throw e;
        }
        if (res.getStatus() >= 500) {
            webhookSecurityService.releaseEvent(eventId);
        }
    }

    /** The shop's own app secret first (each Shopify app signs with its client secret). */
    private List<String> shopifySecrets(String shopDomain) {
        List<String> secrets = new ArrayList<>(2);
        if (shopDomain != null && !shopDomain.isBlank()) {
            try {
                storeService.findWebhookSecretForDomain(shopDomain).ifPresent(secrets::add);
            } catch (RuntimeException e) {
                log.warn("Could not load webhook secret for {}: {}", shopDomain, e.getMessage());
            }
        }
        secrets.add(webhookSecurityService.globalShopifySecret());
        return secrets;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }
}
