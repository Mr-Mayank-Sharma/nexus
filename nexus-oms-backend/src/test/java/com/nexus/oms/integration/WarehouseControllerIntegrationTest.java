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
    private static UUID zoneId;
    private static UUID binId;
    private static UUID staffId;

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
    @Order(5)
    void createZone_ReturnsCreated() throws Exception {
        assertNotNull(warehouseId);

        String body = """
                {
                    "warehouseId": "%s",
                    "code": "ZN-INT-001",
                    "name": "Cold Storage Zone",
                    "zoneType": "COLD",
                    "zoneCategory": "TEMPERATURE_CONTROLLED"
                }
                """.formatted(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/zones", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        zoneId = UUID.fromString(json.get("data").get("id").asText());
        assertNotNull(zoneId);
    }

    @Test
    @Order(6)
    void getZones_ReturnsCreatedZone() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId + "/zones", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("data").size() >= 1);
    }

    @Test
    @Order(7)
    void createBin_ReturnsCreated() throws Exception {
        assertNotNull(warehouseId);

        String body = """
                {
                    "warehouseId": "%s",
                    "code": "BIN-INT-001",
                    "binType": "PALLET",
                    "binClass": "A",
                    "isEmpty": true
                }
                """.formatted(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/bins", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        binId = UUID.fromString(json.get("data").get("id").asText());
        assertNotNull(binId);
        assertTrue(json.get("data").get("isEmpty").asBoolean());
    }

    @Test
    @Order(8)
    void getBins_ReturnsCreatedBin() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId + "/bins", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("data").size() >= 1);
    }

    @Test
    @Order(9)
    void getEmptyBins_ReturnsBins() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId + "/bins/empty", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    @Order(10)
    void reserveBin_MarksReserved() throws Exception {
        assertNotNull(binId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/bins/" + binId + "/reserve", HttpMethod.PUT,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("data").get("isReserved").asBoolean());
    }

    @Test
    @Order(11)
    void releaseBin_MarksUnreserved() throws Exception {
        assertNotNull(binId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/bins/" + binId + "/release", HttpMethod.PUT,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertFalse(json.get("data").get("isReserved").asBoolean());
    }

    @Test
    @Order(12)
    void createStaff_ReturnsCreated() throws Exception {
        assertNotNull(warehouseId);

        String body = """
                {
                    "warehouseId": "%s",
                    "employeeCode": "EMP-INT-001",
                    "firstName": "Test",
                    "lastName": "Picker",
                    "role": "PICKER",
                    "isActive": true
                }
                """.formatted(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/staff", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());

        staffId = UUID.fromString(json.get("data").get("id").asText());
        assertNotNull(staffId);
    }

    @Test
    @Order(13)
    void getStaff_ReturnsCreatedStaff() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId + "/staff", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("data").size() >= 1);
    }

    @Test
    @Order(14)
    void incrementPickCount_Increments() throws Exception {
        assertNotNull(staffId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/staff/" + staffId + "/increment-picks", HttpMethod.PUT,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    @Order(15)
    void createEquipment_ReturnsCreated() throws Exception {
        assertNotNull(warehouseId);

        String body = """
                {
                    "warehouseId": "%s",
                    "code": "EQ-INT-001",
                    "equipmentType": "FORKLIFT",
                    "status": "AVAILABLE",
                    "isActive": true
                }
                """.formatted(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/equipment", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    @Order(16)
    void getEquipment_ReturnsList() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
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
    void deleteWarehouse_FailsWithExistingBins() throws Exception {
        assertNotNull(warehouseId);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/warehouses/" + warehouseId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
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
