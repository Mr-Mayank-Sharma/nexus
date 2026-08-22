package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
        if (json == null || json.isBlank()) return null;
        try {
            if (clazz == JsonNode.class) {
                return (T) objectMapper.readTree(json);
            }
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BigCommerce response", e);
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getOrders(String apiPath, String accessToken, Map<String, String> params) {
        RestClient client = buildClient(apiPath, accessToken);
        String uri = buildUri(apiPath + "/v2/orders", params);
        try {
            String response = client.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch orders from BigCommerce: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getOrderById(String apiPath, String accessToken, int orderId) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String response = client.get()
                    .uri(apiPath + "/v2/orders/" + orderId)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch order " + orderId + ": " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode updateOrderStatus(String apiPath, String accessToken, int orderId, int statusId) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            Map<String, Object> statusData = Map.of("status_id", statusId);
            String body = objectMapper.writeValueAsString(statusData);
            String response = client.put()
                    .uri(apiPath + "/v2/orders/" + orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to update status for order " + orderId + ": " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize order status data", e);
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getCustomers(String apiPath, String accessToken, Map<String, String> params) {
        RestClient client = buildClient(apiPath, accessToken);
        String uri = buildUri(apiPath + "/v3/customers", params);
        try {
            String response = client.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch customers from BigCommerce: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getOrderProducts(String apiPath, String accessToken, int orderId) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String response = client.get()
                    .uri(apiPath + "/v2/orders/" + orderId + "/products")
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch products for order " + orderId + ": " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getOrderShippingAddresses(String apiPath, String accessToken, int orderId) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String response = client.get()
                    .uri(apiPath + "/v2/orders/" + orderId + "/shipping_addresses")
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch shipping addresses for order " + orderId + ": " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getProducts(String apiPath, String accessToken, Map<String, String> params) {
        RestClient client = buildClient(apiPath, accessToken);
        String uri = buildUri(apiPath + "/v3/catalog/products", params);
        try {
            String response = client.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch products: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode getProductVariants(String apiPath, String accessToken, int productId) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String response = client.get()
                    .uri(apiPath + "/v3/catalog/products/" + productId + "/variants")
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, JsonNode.class);
        } catch (RestClientException e) {
            throw new BadRequestException("Failed to fetch variants for product " + productId + ": " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode updateInventory(String apiPath, String accessToken, long productId, Map<String, Object> inventoryData) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String body = objectMapper.writeValueAsString(inventoryData);
            String response = client.put()
                    .uri(apiPath + "/v3/catalog/products/" + productId)
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

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode createShipment(String apiPath, String accessToken, int orderId, Map<String, Object> shipmentData) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String body = objectMapper.writeValueAsString(shipmentData);
            String response = client.post()
                    .uri(apiPath + "/v2/orders/" + orderId + "/shipments")
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

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode createRefund(String apiPath, String accessToken, int orderId, Map<String, Object> refundData) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String body = objectMapper.writeValueAsString(refundData);
            String response = client.post()
                    .uri(apiPath + "/v3/orders/" + orderId + "/refunds")
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

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode registerWebhook(String apiPath, String accessToken, String scope, String destination) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            Map<String, Object> hookData = new HashMap<>();
            hookData.put("scope", scope);
            hookData.put("destination", destination);
            hookData.put("is_active", true);
            String body = objectMapper.writeValueAsString(hookData);
            String response = client.post()
                    .uri(apiPath + "/v3/hooks")
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

    @CircuitBreaker(name = "bigcommerce-api")
    public JsonNode deleteWebhook(String apiPath, String accessToken, int webhookId) {
        RestClient client = buildClient(apiPath, accessToken);
        try {
            String response = client.delete()
                    .uri(apiPath + "/v3/hooks/" + webhookId)
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
}
