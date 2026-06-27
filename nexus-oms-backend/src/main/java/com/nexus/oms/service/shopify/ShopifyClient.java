package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class ShopifyClient {

    private static final String API_VERSION = "2024-10";
    private final ObjectMapper objectMapper;

    public ShopifyClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private RestClient buildClient(String shopDomain, String accessToken) {
        String baseUrl = "https://" + shopDomain + "/admin/api/" + API_VERSION;
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

    public JsonNode getOrderById(String shopDomain, String accessToken, long orderId) {
        return doGet(buildClient(shopDomain, accessToken), "/orders/" + orderId + ".json", null);
    }

    public JsonNode getProducts(String shopDomain, String accessToken, Map<String, String> params) {
        return doGet(buildClient(shopDomain, accessToken), "/products.json", params);
    }

    public JsonNode getInventoryLevels(String shopDomain, String accessToken, Map<String, String> params) {
        return doGet(buildClient(shopDomain, accessToken), "/inventory_levels.json", params);
    }

    public JsonNode adjustInventoryLevel(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/inventory_levels/adjust.json", data);
    }

    public JsonNode setInventoryLevel(String shopDomain, String accessToken, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/inventory_levels/set.json", data);
    }

    public JsonNode createFulfillment(String shopDomain, String accessToken, long orderId, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/orders/" + orderId + "/fulfillments.json", data);
    }

    public JsonNode createRefund(String shopDomain, String accessToken, long orderId, Map<String, Object> data) {
        return doPost(buildClient(shopDomain, accessToken), "/orders/" + orderId + "/refunds.json", data);
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

    private JsonNode doGet(RestClient client, String path, Map<String, String> params) {
        try {
            String uri = path;
            if (params != null && !params.isEmpty()) {
                StringBuilder qs = new StringBuilder("?");
                params.forEach((k, v) -> qs.append(k).append("=").append(v).append("&"));
                uri = path + "?" + qs.substring(0, qs.length() - 1);
            }
            String response = client.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (RestClientException e) {
            throw new BadRequestException("Shopify API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Shopify response", e);
        }
    }

    private JsonNode doPost(RestClient client, String path, Map<String, Object> data) {
        try {
            String body = objectMapper.writeValueAsString(data);
            String response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (RestClientException e) {
            throw new BadRequestException("Shopify API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to process Shopify request", e);
        }
    }
}
