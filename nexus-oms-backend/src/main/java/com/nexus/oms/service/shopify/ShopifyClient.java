package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class ShopifyClient {

    /**
     * Admin API version. Shopify supports each version for about a year and then silently moves
     * requests to the oldest supported one, so the pin must be revisited regularly. Override with
     * app.shopify.api-version and re-test against a development store when it changes.
     */
    @org.springframework.beans.factory.annotation.Value("${app.shopify.api-version:2026-07}")
    private String apiVersion = "2026-07";

    static final int MAX_ATTEMPTS = 4;
    private static final long MAX_BACKOFF_MS = 10_000;
    private final ObjectMapper objectMapper;

    public ShopifyClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private RestClient buildClient(String shopDomain, String accessToken) {
        String baseUrl = "https://" + shopDomain + "/admin/api/" + apiVersion;
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Shopify-Access-Token", accessToken)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public JsonNode getOrders(String shopDomain, String accessToken, Map<String, String> params) {
        return doGet(buildClient(shopDomain, accessToken), "/orders.json", params);
    }

    public ProductPage getOrdersPage(String shopDomain, String accessToken, Map<String, String> params) {
        return doGetWithLink(buildClient(shopDomain, accessToken), "/orders.json", params);
    }

    public JsonNode getOrderById(String shopDomain, String accessToken, long orderId) {
        return doGet(buildClient(shopDomain, accessToken), "/orders/" + orderId + ".json", null);
    }

    public JsonNode getProducts(String shopDomain, String accessToken, Map<String, String> params) {
        return doGet(buildClient(shopDomain, accessToken), "/products.json", params);
    }

    public ProductPage getProductsPage(String shopDomain, String accessToken, Map<String, String> params) {
        return doGetWithLink(buildClient(shopDomain, accessToken), "/products.json", params);
    }

    public record ProductPage(JsonNode body, String nextPageInfo) {
    }

    public JsonNode getInventoryLevels(String shopDomain, String accessToken, Map<String, String> params) {
        return doGet(buildClient(shopDomain, accessToken), "/inventory_levels.json", params);
    }

    public JsonNode getLocations(String shopDomain, String accessToken) {
        return doGet(buildClient(shopDomain, accessToken), "/locations.json", null);
    }

    public JsonNode adjustInventoryLevel(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/inventory_levels/adjust.json", data);
    }

    /** Stocks an inventory item at a location; required before a quantity can be set there. */
    public JsonNode connectInventoryLevel(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/inventory_levels/connect.json", data);
    }

    public JsonNode setInventoryLevel(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/inventory_levels/set.json", data);
    }

    public JsonNode createFulfillment(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/fulfillments.json", data);
    }

    public JsonNode getOrderFulfillmentOrders(String shopDomain, String accessToken, long orderId) {
        return doGet(buildClient(shopDomain, accessToken), "/orders/" + orderId + "/fulfillment_orders.json", null);
    }

    public JsonNode createRefund(String shopDomain, String accessToken, long orderId, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/orders/" + orderId + "/refunds.json", data);
    }

    public JsonNode getWebhooks(String shopDomain, String accessToken) {
        return doGet(buildClient(shopDomain, accessToken), "/webhooks.json", Map.of("limit", "250"));
    }

    public JsonNode registerWebhook(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/webhooks.json", data);
    }

    public JsonNode deleteWebhook(String shopDomain, String accessToken, long webhookId) {
        RestClient client = buildClient(shopDomain, accessToken);
        try {
            String response = client.delete()
                    .uri("/webhooks/{id}.json", webhookId)
                    .retrieve()
                    .body(String.class);
            return response != null ? objectMapper.readTree(response) : null;
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to delete Shopify webhook: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response", e);
        }
    }

    /**
     * Runs one HTTP call, retrying while Shopify throttles us (429) and, for reads only, on
     * transient 502/503/504. Waits for Retry-After when given, else backs off exponentially.
     */
    <T> T retrying(boolean idempotent, java.util.function.Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (org.springframework.web.client.RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || (idempotent && (status == 502 || status == 503 || status == 504));
                if (!retryable || attempt >= MAX_ATTEMPTS) throw e;
                pause(backoffMillis(e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null, attempt));
            }
        }
    }

    private static String withQuery(String path, Map<String, String> params) {
        if (params == null || params.isEmpty()) return path;
        StringBuilder qs = new StringBuilder();
        params.forEach((k, v) -> qs.append(k).append("=").append(v).append("&"));
        return path + "?" + qs.substring(0, qs.length() - 1);
    }

    static long backoffMillis(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try {
                return Math.min(MAX_BACKOFF_MS, Math.max(0L, (long) (Double.parseDouble(retryAfter.trim()) * 1000)));
            } catch (NumberFormatException ignored) {
                // fall through to exponential backoff
            }
        }
        return Math.min(MAX_BACKOFF_MS, 500L * (1L << (attempt - 1)));
    }

    void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Shopify", e);
        }
    }

    @CircuitBreaker(name = "shopify-api")
    private JsonNode doGet(RestClient client, String path, Map<String, String> params) {
        try {
            final String uri = withQuery(path, params);
            String response = retrying(true, () -> client.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class));
            return objectMapper.readTree(response);
        } catch (RestClientException e) {
            throw new BadRequestException("Shopify API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Shopify response", e);
        }
    }

    @CircuitBreaker(name = "shopify-api")
    private ProductPage doGetWithLink(RestClient client, String path, Map<String, String> params) {
        try {
            final String uri = withQuery(path, params);
            var response = retrying(true, () -> client.get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(String.class));
            JsonNode body = objectMapper.readTree(response.getBody());
            String next = null;
            String link = response.getHeaders().getFirst("Link");
            if (link != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("<([^>]+)>\\s*;\\s*rel=\"next\"")
                        .matcher(link);
                if (m.find()) {
                    String nextUrl = m.group(1);
                    java.util.regex.Matcher qm = java.util.regex.Pattern
                            .compile("[?&]page_info=([^&]+)")
                            .matcher(nextUrl);
                    if (qm.find()) {
                        next = qm.group(1);
                    } else {
                        next = nextUrl;
                    }
                }
            }
            return new ProductPage(body, next);
        } catch (RestClientException e) {
            throw new BadRequestException("Shopify API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Shopify response", e);
        }
    }

    @CircuitBreaker(name = "shopify-api")
    private JsonNode doPost(RestClient client, String path, Map<String, Object> data) {
        try {
            String body = objectMapper.writeValueAsString(data);
            String response = retrying(false, () -> client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class));
            return objectMapper.readTree(response);
        } catch (RestClientException e) {
            throw new BadRequestException("Shopify API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to process Shopify request", e);
        }
    }
}
