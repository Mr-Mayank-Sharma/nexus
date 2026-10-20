package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WarehouseControllerIntegrationTest extends AbstractIntegrationTest {

    private static UUID warehouseId;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            registerAdminUser();
        }
    }

    @Test
    @Order(1)
    void createWarehouse_ReturnsCreated() throws Exception {
        String body = """
                {
                    "code": "WH-INT-001",
                    "name": "Integration Test Warehouse",
                    "type": "DISTRIBUTION",
                    "status": "ACTIVE",
                    "city": "Chicago",
                    "state": "IL",
                    "country": "US",
                    "zipCode": "60601",
                    "dockCount": 8,
                    "contactName": "WH Manager",
                    "contactPhone": "312-555-0100",
                    "contactEmail": "wh@test.com"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        warehouseId = UUID.fromString(json.get("data").get("id").asText());
        assertNotNull(warehouseId);
        assertEquals("WH-INT-001", json.get("data").get("code").asText());
        assertEquals("Integration Test Warehouse", json.get("data").get("name").asText());
    }

    @Test
    @Order(2)
    void getWarehouse_ReturnsCreatedWarehouse() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals(warehouseId.toString(), json.get("data").get("id").asText());
        assertEquals("WH-INT-001", json.get("data").get("code").asText());
    }

    @Test
    @Order(3)
    void getWarehouses_ListReturnsPage() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("data").get("content").size() >= 1);
    }

    @Test
    @Order(4)
    void updateWarehouse_ModifiesFields() throws Exception {
        assertNotNull(warehouseId);

        String body = """
                {
                    "code": "WH-INT-001",
                    "name": "Updated Warehouse Name",
                    "type": "DISTRIBUTION",
                    "city": "Chicago",
                    "state": "IL",
                    "country": "US"
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertEquals("Updated Warehouse Name", json.get("data").get("name").asText());
    }

    @Test
    @Order(17)
    void getWarehouseSummary_ReturnsData() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId + "/summary", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    @Order(18)
    void getAllWarehousesSummary_ReturnsList() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/summary", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    @Order(19)
    void deleteWarehouse_RemovesIt() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(20)
    void createWarehouse_MissingCode_ReturnsError() throws Exception {
        String body = """
                {"name": "No Code Warehouse"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(21)
    void warehouseEndpoints_Unauthenticated_Returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
