package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static UUID createdOrderId;
    private static UUID warehouseNodeId;
    private static final String TEST_SKU = "IT-SKU-001";

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            registerAdminUser();
            warehouseNodeId = initializeWarehouse("Integration Test WH");
            initializeInventory(warehouseNodeId, TEST_SKU, 100);
            initializeInventory(warehouseNodeId, "IT-SKU-002", 50);
        }
    }

    @Test
    @Order(1)
    void testHealthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(2)
    void testAdminRegistration() {
        assertNotNull(adminToken);
        assertNotNull(tenantId);
    }

    @Test
    @Order(3)
    void testCreateOrder() throws Exception {
        String orderBody = """
                {
                    "customerEmail": "order-lifecycle-%d@test.com",
                    "customerName": "Lifecycle Test",
                    "shippingAddress": {"line1": "456 Lifecycle Ave", "city": "Portland", "state": "OR", "pincode": "97201"},
                    "channel": "WEB",
                    "items": [
                        {"sku": "%s", "productName": "Test Product One", "quantity": 2, "unitPrice": 15.00}
                    ]
                }
                """.formatted(System.currentTimeMillis(), TEST_SKU);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders",
                HttpMethod.POST,
                new HttpEntity<>(orderBody, authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        JsonNode data = json.get("data");
        assertEquals("PENDING", data.get("status").asText());
        assertEquals("WEB", data.get("channel").asText());
        assertTrue(data.has("id"), "Order ID should be present");
        assertEquals(2, data.get("items").get(0).get("quantity").asInt());

        createdOrderId = UUID.fromString(data.get("id").asText());
    }

    @Test
    @Order(4)
    void testGetOrderById() throws Exception {
        assertNotNull(createdOrderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + createdOrderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals(createdOrderId.toString(), json.get("data").get("id").asText());
        assertEquals("PENDING", json.get("data").get("status").asText());
    }

    @Test
    @Order(5)
    void testListOrders() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        JsonNode content = json.get("data").get("content");
        assertTrue(content.isArray());
        assertTrue(content.size() >= 1, "Should have at least 1 order");
    }

    @Test
    @Order(6)
    void testConfirmOrder() throws Exception {
        assertNotNull(createdOrderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + createdOrderId + "/confirm",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("CONFIRMED", json.get("data").get("status").asText());
    }

    @Test
    @Order(7)
    void testAllocateOrder() throws Exception {
        assertNotNull(createdOrderId);
        assertNotNull(warehouseNodeId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + createdOrderId + "/allocate",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("Order allocated", json.get("message").asText());

        JsonNode data = json.get("data");
        assertTrue(data.has("warehouse"));
        assertTrue(data.has("confidence"));
        assertTrue(data.has("rule"));
    }

    @Test
    @Order(8)
    void testAllocatedOrderStatus() throws Exception {
        assertNotNull(createdOrderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + createdOrderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertEquals("ALLOCATED", json.get("data").get("status").asText());
    }

    @Test
    @Order(9)
    void testShipOrder() throws Exception {
        assertNotNull(createdOrderId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + createdOrderId + "/ship?carrierId=FEDEX&trackingNumber=TRACK-INT-001",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("SHIPPED", json.get("data").get("status").asText());
        assertEquals("FEDEX", json.get("data").get("carrierId").asText());
        assertEquals("TRACK-INT-001", json.get("data").get("trackingNumber").asText());
    }

    @Test
    @Order(10)
    void testUpdateOrderStatus() throws Exception {
        assertNotNull(createdOrderId);

        String statusBody = """
                {"status": "DELIVERED", "subStatus": "SIGNED"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + createdOrderId + "/status",
                HttpMethod.PUT,
                new HttpEntity<>(statusBody, authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("DELIVERED", json.get("data").get("status").asText());
        assertEquals("SIGNED", json.get("data").get("subStatus").asText());
    }

    @Test
    @Order(11)
    void testFilterOrdersByStatus() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders?status=DELIVERED&page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        JsonNode content = json.get("data").get("content");
        assertTrue(content.isArray());
        assertTrue(content.size() >= 1);
        assertEquals("DELIVERED", content.get(0).get("status").asText());
    }

    @Test
    @Order(12)
    void testCreateAndCancelOrder() throws Exception {
        String orderBody = """
                {
                    "customerEmail": "cancel-test-%d@test.com",
                    "customerName": "Cancel Test",
                    "shippingAddress": {"line1": "789 Cancel Blvd", "city": "Denver", "state": "CO", "pincode": "80201"},
                    "channel": "WEB",
                    "items": [
                        {"sku": "IT-SKU-002", "productName": "Cancel Product", "quantity": 1, "unitPrice": 25.00}
                    ]
                }
                """.formatted(System.currentTimeMillis());

        ResponseEntity<String> createResp = restTemplate.exchange(
                baseUrl() + "/orders",
                HttpMethod.POST,
                new HttpEntity<>(orderBody, authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        JsonNode createJson = objectMapper.readTree(createResp.getBody());
        UUID cancelOrderId = UUID.fromString(createJson.get("data").get("id").asText());

        ResponseEntity<String> cancelResp = restTemplate.exchange(
                baseUrl() + "/orders/" + cancelOrderId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
        JsonNode cancelJson = objectMapper.readTree(cancelResp.getBody());
        assertTrue(cancelJson.get("success").asBoolean());
        assertEquals("CANCELLED", cancelJson.get("data").get("status").asText());
    }

    @Test
    @Order(13)
    void testGetOrderNotFound() {
        UUID fakeId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders/" + fakeId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @Order(14)
    void testUnauthenticatedAccess() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders",
                HttpMethod.GET,
                null,
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(15)
    void testSearchOrders() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders?search=Lifecycle&page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        JsonNode content = json.get("data").get("content");
        assertTrue(content.isArray());
        assertTrue(content.size() >= 1, "Search should find Lifecycle orders");
    }
}
