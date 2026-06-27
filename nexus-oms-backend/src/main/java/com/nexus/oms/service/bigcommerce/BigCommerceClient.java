package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.MediaType;

import java.util.*;

@Component
public class BigCommerceClient {

    private final ObjectMapper objectMapper;

    public BigCommerceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private RestClient buildClient(String apiPath, String accessToken) {
        return RestClient.builder()
                .baseUrl(apiPath)
                .defaultHeader("X-Auth-Token", accessToken)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T parseResponse(String json, Class<T> clazz) {
        try {
            if (clazz == JsonNode.class) {
                return (T) objectMapper.readTree(json);
            }
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BigCommerce response", e);
        }
    }

    public JsonNode getOrders(String apiPath, String accessToken, Map<String, String> params) {
        RestClient client = buildClient(apiPath, accessToken);
        StringBuilder path = new StringBuilder("/stores/{storeHash}/v2/orders");
        String storeHash = extractStoreHash(apiPath);

        String uri = buildUri(path.toString(), params);
        try {
            String response = client.get()
                    .uri(uri, storeHash)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch orders from BigCommerce: " + e.getMessage());
        }
    }

    public JsonNode getOrderById(String apiPath, String accessToken, int orderId) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            String response = client.get()
                    .uri("/stores/{storeHash}/v2/orders/{orderId}", storeHash, orderId)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch order " + orderId + ": " + e.getMessage());
        }
    }

    public JsonNode getProducts(String apiPath, String accessToken, Map<String, String> params) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        String uri = buildUri("/stores/{storeHash}/v3/catalog/products", params);
        try {
            String response = client.get()
                    .uri(uri, storeHash)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch products: " + e.getMessage());
        }
    }

    public JsonNode getProductVariants(String apiPath, String accessToken, int productId) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            String response = client.get()
                    .uri("/stores/{storeHash}/v3/catalog/products/{productId}/variants", storeHash, productId)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch variants for product " + productId + ": " + e.getMessage());
        }
    }

    public JsonNode updateInventory(String apiPath, String accessToken, int productId, Map<String, Object> inventoryData) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            String body = objectMapper.writeValueAsString(inventoryData);
            String response = client.put()
                    .uri("/stores/{storeHash}/v3/catalog/products/{productId}/inventory", storeHash, productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to update inventory for product " + productId + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize inventory data", e);
        }
    }

    public JsonNode createShipment(String apiPath, String accessToken, int orderId, Map<String, Object> shipmentData) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            String body = objectMapper.writeValueAsString(shipmentData);
            String response = client.post()
                    .uri("/stores/{storeHash}/v2/orders/{orderId}/shipments", storeHash, orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to create shipment for order " + orderId + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize shipment data", e);
        }
    }

    public JsonNode createRefund(String apiPath, String accessToken, int orderId, Map<String, Object> refundData) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            String body = objectMapper.writeValueAsString(refundData);
            String response = client.post()
                    .uri("/stores/{storeHash}/v3/orders/{orderId}/refunds", storeHash, orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to create refund for order " + orderId + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize refund data", e);
        }
    }

    public JsonNode registerWebhook(String apiPath, String accessToken, String scope, String destination) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            Map<String, Object> hookData = new HashMap<>();
            hookData.put("scope", scope);
            hookData.put("destination", destination);
            hookData.put("is_active", true);
            String body = objectMapper.writeValueAsString(hookData);
            String response = client.post()
                    .uri("/stores/{storeHash}/v3/hooks", storeHash)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to register webhook: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize webhook data", e);
        }
    }

    public JsonNode deleteWebhook(String apiPath, String accessToken, int webhookId) {
        RestClient client = buildClient(apiPath, accessToken);
        String storeHash = extractStoreHash(apiPath);
        try {
            String response = client.delete()
                    .uri("/stores/{storeHash}/v3/hooks/{webhookId}", storeHash, webhookId)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to delete webhook: " + e.getMessage());
        }
    }

    private String buildUri(String basePath, Map<String, String> params) {
        if (params == null || params.isEmpty()) return basePath;
        StringBuilder uri = new StringBuilder(basePath).append("?");
        params.forEach((k, v) -> uri.append(k).append("=").append(v).append("&"));
        return uri.substring(0, uri.length() - 1);
    }

    private String extractStoreHash(String apiPath) {
        return "";
    }
}
