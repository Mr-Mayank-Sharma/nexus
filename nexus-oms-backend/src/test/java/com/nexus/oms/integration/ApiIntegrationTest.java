package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest extends AbstractIntegrationTest {

    private static String viewerToken;
    private static UUID viewerTenantId;
    private static boolean initialized;

    @BeforeEach
    void setUp() throws Exception {
        // Guard on this class's own sentinel, not on adminToken. adminToken is
        // static and shared with every other AbstractIntegrationTest subclass,
        // so an alphabetically earlier sibling (AnalyticsControllerIntegrationTest)
        // may already have registered an admin by the time this class runs. Keying
        // off adminToken then skipped this method's body entirely and left
        // viewerToken null, failing the three viewer tests with a bare
        // "expected: not <null>". Order-dependent test, invisible in isolation.
        if (!initialized) {
            if (adminToken == null) {
                registerAdminUser();
            }
            registerViewerUser();
            seedViewerPermissions();
            initialized = true;
        }
    }

    private void registerViewerUser() throws Exception {
        long ts = System.currentTimeMillis();
        String body = """
                {"username": "intviewer-%d", "password": "Test1234!", "role": "VIEWER"}
                """.formatted(ts);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl() + "/auth/register",
                new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode data = objectMapper.readTree(resp.getBody()).get("data");
        viewerToken = data.get("accessToken").asText();
        viewerTenantId = UUID.fromString(data.get("tenantId").asText());
    }

    private void seedViewerPermissions() {
        seedPermission(viewerTenantId, "VIEWER", "orders", "view", true, false);
    }

    @Test
    @Order(1)
    void testHealthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(2)
    void testAdminCanCreateOrder() throws Exception {
        assertNotNull(adminToken);

        String orderBody = """
                {
                    "customerEmail": "inttest-%s@test.com",
                    "customerName": "Integration Test",
                    "shippingAddress": {"line1": "123 Test St", "city": "NYC", "state": "NY", "pincode": "10001"},
                    "channel": "WEB",
                    "items": [{"sku": "IT-SKU-001", "productName": "Test Product", "quantity": 1, "unitPrice": 10.00}]
                }
                """.formatted(System.currentTimeMillis());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(orderBody, authHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertEquals(true, json.get("success").asBoolean());
    }

    @Test
    @Order(3)
    void testViewerCannotCreateOrder() {
        assertNotNull(viewerToken);

        String orderBody = """
                {"customerEmail": "blocked@test.com", "customerName": "Blocked", "shippingAddress": {"line1": "1"}, "channel": "WEB", "items": []}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(orderBody, authHeaders(viewerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @Order(4)
    void testViewerCanReadOrders() {
        assertNotNull(viewerToken);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.GET,
                new HttpEntity<>(authHeaders(viewerToken)), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(5)
    void testViewerCannotAccessInventory() {
        assertNotNull(viewerToken);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/inventory", HttpMethod.GET,
                new HttpEntity<>(authHeaders(viewerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
