package com.nexus.oms.filter;

import com.nexus.oms.security.WebhookSecurityService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

@Component
@Order(2)
public class WebhookAuthenticationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(WebhookAuthenticationFilter.class);

    private final WebhookSecurityService webhookSecurityService;

    public WebhookAuthenticationFilter(WebhookSecurityService webhookSecurityService) {
        this.webhookSecurityService = webhookSecurityService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (path.contains("/webhooks/")) {
            String body = new BufferedReader(req.getReader()).lines().collect(Collectors.joining("\n"));
            String eventId = req.getHeader("X-Shopify-Webhook-Id");
            if (eventId == null) eventId = req.getHeader("X-Bc-Webhook-Id");
            if (eventId == null) eventId = req.getHeader("X-Event-Id");

            boolean valid = false;
            String source = "";

            if (path.contains("shopify") && eventId != null) {
                String hmac = req.getHeader("X-Shopify-Hmac-Sha256");
                valid = webhookSecurityService.verifyShopifyHmac(body, hmac);
                source = "shopify";
            } else if (path.contains("bigcommerce") && eventId != null) {
                String sig = req.getHeader("X-Bc-Webhook-Signature");
                valid = webhookSecurityService.verifyBigCommerceHmac(body, sig);
                source = "bigcommerce";
            }

            // Always check for duplicate events (for both valid and invalid signatures)
            if (eventId != null && (path.contains("shopify") || path.contains("bigcommerce"))) {
                boolean duplicate = webhookSecurityService.isDuplicateEvent(eventId);
                webhookSecurityService.logWebhookAttempt(source, eventId, valid, "hmac=" + valid + " duplicate=" + duplicate);

                if (duplicate) {
                    log.info("Duplicate webhook event rejected: source={} eventId={}", source, eventId);
                    res.setStatus(200);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"status\":\"ok\",\"message\":\"duplicate event ignored\"}");
                    return;
                }
            }

            if (!valid && (path.contains("shopify") || path.contains("bigcommerce"))) {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid webhook signature\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
