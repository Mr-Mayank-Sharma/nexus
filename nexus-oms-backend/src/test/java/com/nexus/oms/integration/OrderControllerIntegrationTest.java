package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    private static UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            registerAdminUser();
        }
    }

    private String orderJson(String sku, int qty, double price) {
        return """
                {
                    "channel": "WEB",
                    "customerName": "Integ Test Customer",
                    "customerEmail": "integ-%d@test.com",
                    "currency": "USD",
                    "shippingAddress": {
                        "line1": "456 Integration Ave",
                        "city": "San Francisco",
                        "state": "CA",
                        "pincode": "94105",
                        "country": "US"
                    },
                    "items": [
                        {
                            "sku": "%s",
                            "productName": "Test Product",
                            "quantity": %d,
                            "unitPrice": %.2f
                        }
                    ]
                }
                """.formatted(System.currentTimeMillis(), sku, qty, price);
    }

    @Test
    @Order(1)
    void createOrder_ReturnsSuccess() throws Exception {
        String body = orderJson("ORD-INT-001", 2, 25.50);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertNotNull(json.get("data"));

        orderId = UUID.fromString(json.get("data").get("id").asText());
        assertNotNull(orderId);

        String status = json.get("data").get("status").asText();
        assertEquals("PENDING", status);
    }

    @Test
    @Order(2)
    void getOrder_ReturnsCreatedOrder() throws Exception {
        assertNotNull(orderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals(orderId.toString(), json.get("data").get("id").asText());
        assertEquals("WEB", json.get("data").get("channel").asText());
    }

    @Test
    @Order(3)
    void getOrders_ListReturnsPage() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertNotNull(json.get("data").get("content"));
        assertTrue(json.get("data").get("content").size() >= 1);
    }

    @Test
    @Order(4)
    void confirmOrder_TransitionsToConfirmed() throws Exception {
        assertNotNull(orderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + orderId + "/confirm", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("CONFIRMED", json.get("data").get("status").asText());
    }

    @Test
    @Order(5)
    void updateStatus_TransitionsOrder() throws Exception {
        assertNotNull(orderId);

        String body = """
                {"status": "PROCESSING"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + orderId + "/status", HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("PROCESSING", json.get("data").get("status").asText());
    }

    @Test
    @Order(6)
    void shipOrder_TransitionsToShipped() throws Exception {
        assertNotNull(orderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + orderId + "/ship?carrierId=UPS&trackingNumber=1Z999AA10123456784",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("SHIPPED", json.get("data").get("status").asText());
    }

    @Test
    @Order(7)
    void createSecondOrder_ForCancelTest() throws Exception {
        String body = orderJson("ORD-INT-002", 1, 9.99);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    @Order(8)
    void cancelOrder_TransitionsToCancelled() throws Exception {
        String body = orderJson("ORD-INT-003", 1, 5.00);

        ResponseEntity<String> createResp = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);
        JsonNode createJson = objectMapper.readTree(createResp.getBody());
        UUID cancelId = UUID.fromString(createJson.get("data").get("id").asText());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + cancelId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertEquals("CANCELLED", json.get("data").get("status").asText());
    }

    @Test
    @Order(9)
    void getOrder_NonExistentId_ReturnsError() {
        UUID fakeId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + fakeId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @Order(10)
    void createOrder_MissingRequiredFields_ReturnsError() {
        String body = """
                {"channel": "WEB"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(11)
    void createOrder_Unauthenticated_Returns401() {
        String body = orderJson("ORD-INT-004", 1, 10.00);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(body, new HttpHeaders()), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
