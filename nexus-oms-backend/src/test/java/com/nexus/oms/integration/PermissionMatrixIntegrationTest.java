package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionMatrixIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> tokens = new LinkedHashMap<>();

    private static final String[] ALL_ROLES = {
        "VIEWER", "CEO",
        "CUSTOMER_SUPPORT", "FINANCE", "PROCUREMENT_MANAGER",
        "OPS_MANAGER", "LOGISTICS_MANAGER",
        "WAREHOUSE_MANAGER", "STORE_MANAGER", "BOPIS_OWNER",
        "PICKER", "PACKER", "LOADER",
        "ADMIN"
    };

    private static final List<String> ALL_RESOURCES = List.of(
        "orders", "inventory", "products", "customers", "returns", "shipments",
        "picking", "packing", "shipping", "warehouse", "routing",
        "procurement", "invoices", "payments", "carriers", "rbac",
        "settings", "workflows", "ai", "analytics", "documents",
        "notifications", "webhooks", "edi", "cycle-counts",
        "inventory-receipts", "rate-shopping", "routing-rules",
        "audit", "integration", "fulfillment"
    );

    private static final String PAYLOAD = "{\"name\":\"pmt-test\"}";

    @Test
    @Order(1)
    void testRegisterAllRoles() throws Exception {
        long ts = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (String role : ALL_ROLES) {
            String body = """
                    {"username": "pmt-%s-%d", "password": "Test1234!", "role": "%s"}
                    """.formatted(role.toLowerCase(), ts, role);
            ResponseEntity<String> resp = restTemplate.exchange(
                    BASE_URL + "/auth/register", HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            assertEquals(HttpStatus.OK, resp.getStatusCode(), "Register " + role);
            JsonNode json = objectMapper.readTree(resp.getBody());
            String token = json.get("data").get("accessToken").asText();
            assertNotNull(token, "Token for " + role);
            tokens.put(role, token);
        }
        assertEquals(14, tokens.size(), "All 14 roles registered");
    }

    @Test
    @Order(2)
    void testAdminHasUnrestrictedAccess() {
        String token = tokens.get("ADMIN");
        assertNotNull(token);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products",
                "/returns", "/shipments", "/picking", "/packing", "/warehouse",
                "/warehouses", "/procurement", "/invoices", "/invoicing",
                "/payments", "/carriers", "/settings", "/workflows",
                "/analytics", "/documents", "/notifications", "/edi",
                "/routing", "/order-routing", "/rbac", "/cycle-counts",
                "/inventory-receipts", "/rate-shopping", "/routing-rules",
                "/audit", "/integration", "/shopify", "/integration-platform",
                "/api/ai", "/fulfillment")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    BASE_URL + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "ADMIN GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
            resp = restTemplate.exchange(
                    BASE_URL + endpoint, HttpMethod.POST,
                    new HttpEntity<>(PAYLOAD, headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
                    "ADMIN POST " + endpoint + " expected 2xx/4xx got " + resp.getStatusCode());
        }
    }

    @Test
    @Order(3)
    void testViewerReadOnlyOnPermittedModules() {
        var token = tokens.get("VIEWER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/orders", "/products", "/analytics", "/shipments", "/customers")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "VIEWER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        for (String endpoint : List.of("/inventory", "/procurement", "/invoices", "/payments",
                "/picking", "/packing", "/warehouse", "/settings", "/rbac")) {
            assertThrows(HttpClientErrorException.Forbidden.class,
                    () -> get(endpoint, headers),
                    "VIEWER GET " + endpoint + " expected 403");
        }

        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products")) {
            assertThrows(HttpClientErrorException.Forbidden.class,
                    () -> post(endpoint, PAYLOAD, headers),
                    "VIEWER POST " + endpoint + " expected 403");
        }

        for (String endpoint : List.of("/orders", "/customers")) {
            assertThrows(HttpClientErrorException.Forbidden.class,
                    () -> delete(endpoint, headers),
                    "VIEWER DELETE " + endpoint + " expected 403");
        }
    }

    @Test
    @Order(4)
    void testCEOReadOnlyOnStrategicModules() {
        var token = tokens.get("CEO");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/analytics", "/ai", "/orders", "/inventory", "/customers",
                "/products", "/returns", "/shipments", "/settings", "/documents", "/audit")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "CEO GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        for (String endpoint : List.of("/orders", "/inventory", "/customers")) {
            assertThrows(HttpClientErrorException.Forbidden.class,
                    () -> post(endpoint, PAYLOAD, headers),
                    "CEO POST " + endpoint + " expected 403");
        }

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> delete("/orders", headers),
                "CEO DELETE expected 403");
    }

    @Test
    @Order(5)
    void testOpsManagerFullCRUDOnOperationalModules() {
        var token = tokens.get("OPS_MANAGER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products",
                "/returns", "/shipments", "/picking", "/packing", "/shipping",
                "/warehouse", "/routing", "/carriers", "/notifications", "/analytics")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "OPS_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products")) {
            var resp = post(endpoint, PAYLOAD, headers);
            assertFalse(resp.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN),
                    "OPS_MANAGER POST " + endpoint + " should not be 403");
        }
    }

    @Test
    @Order(6)
    void testWarehouseManagerFullCRUD() {
        var token = tokens.get("WAREHOUSE_MANAGER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/inventory", "/picking", "/packing", "/shipping",
                "/warehouse", "/shipments", "/notifications", "/analytics")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "WAREHOUSE_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/orders", PAYLOAD, headers),
                "WAREHOUSE_MANAGER POST /orders expected 403");
    }

    @Test
    @Order(7)
    void testPickerScopedToPicking() {
        var token = tokens.get("PICKER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        var pickingResp = get("/picking", headers);
        assertTrue(pickingResp.getStatusCode().is2xxSuccessful(),
                "PICKER GET /picking expected 2xx");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/orders", PAYLOAD, headers),
                "PICKER POST /orders expected 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/inventory", PAYLOAD, headers),
                "PICKER POST /inventory expected 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> delete("/picking", headers),
                "PICKER DELETE /picking expected 403");
    }

    @Test
    @Order(8)
    void testPackerScopedToPacking() {
        var token = tokens.get("PACKER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        var packingResp = get("/packing", headers);
        assertTrue(packingResp.getStatusCode().is2xxSuccessful(),
                "PACKER GET /packing expected 2xx");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/orders", PAYLOAD, headers),
                "PACKER POST /orders expected 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> delete("/packing", headers),
                "PACKER DELETE /packing expected 403");
    }

    @Test
    @Order(9)
    void testLoaderScopedToShipping() {
        var token = tokens.get("LOADER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        var shippingResp = get("/shipping", headers);
        assertTrue(shippingResp.getStatusCode().is2xxSuccessful(),
                "LOADER GET /shipping expected 2xx");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/inventory", PAYLOAD, headers),
                "LOADER POST /inventory expected 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> delete("/shipping", headers),
                "LOADER DELETE /shipping expected 403");
    }

    @Test
    @Order(10)
    void testFinanceScopedToFinancialModules() {
        var token = tokens.get("FINANCE");
        assertNotNull(token);
        var headers = contentHeaders(bearerHeaders(token));

        for (String endpoint : List.of("/invoices", "/invoicing", "/payments", "/returns", "/analytics")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "FINANCE GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/orders", PAYLOAD, headers),
                "FINANCE POST /orders expected 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> delete("/invoices", headers),
                "FINANCE DELETE /invoices expected 403");
    }

    @Test
    @Order(11)
    void testCustomerSupportScoped() {
        var token = tokens.get("CUSTOMER_SUPPORT");
        assertNotNull(token);
        var headers = contentHeaders(bearerHeaders(token));

        for (String endpoint : List.of("/orders", "/customers", "/returns", "/products")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "CUSTOMER_SUPPORT GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        var createOrderResp = post("/orders", PAYLOAD, headers);
        assertFalse(createOrderResp.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN),
                "CUSTOMER_SUPPORT POST /orders should not be 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/inventory", PAYLOAD, headers),
                "CUSTOMER_SUPPORT POST /inventory expected 403");

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> delete("/orders", headers),
                "CUSTOMER_SUPPORT DELETE /orders expected 403");
    }

    @Test
    @Order(12)
    void testProcurementManagerScoped() {
        var token = tokens.get("PROCUREMENT_MANAGER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/procurement", "/invoices", "/products", "/analytics")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "PROCUREMENT_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/orders", PAYLOAD, headers),
                "PROCUREMENT_MANAGER POST /orders expected 403");
    }

    @Test
    @Order(13)
    void testStoreManagerScoped() {
        var token = tokens.get("STORE_MANAGER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products", "/returns")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "STORE_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/picking", PAYLOAD, headers),
                "STORE_MANAGER POST /picking expected 403");
    }

    @Test
    @Order(14)
    void testBopisOwnerScoped() {
        var token = tokens.get("BOPIS_OWNER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/orders", "/inventory", "/customers")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "BOPIS_OWNER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }
    }

    @Test
    @Order(15)
    void testLogisticsManagerScoped() {
        var token = tokens.get("LOGISTICS_MANAGER");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/shipping", "/carriers", "/routing", "/orders",
                "/shipments", "/warehouse", "/analytics")) {
            var resp = get(endpoint, headers);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "LOGISTICS_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        assertThrows(HttpClientErrorException.Forbidden.class,
                () -> post("/invoices", PAYLOAD, headers),
                "LOGISTICS_MANAGER POST /invoices expected 403");
    }

    @Test
    @Order(16)
    void testUnauthenticatedAccessBlocked() {
        assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> get("/orders", new HttpHeaders()));
        assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> get("/inventory", new HttpHeaders()));
        assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> get("/settings", new HttpHeaders()));
    }

    @Test
    @Order(17)
    void testPublicEndpointsAccessible() {
        ResponseEntity<String> healthResp = restTemplate.getForEntity(
                BASE_URL + "/actuator/health", String.class);
        assertTrue(healthResp.getStatusCode().is2xxSuccessful(),
                "Public /actuator/health expected 2xx");

        ResponseEntity<String> authResp = restTemplate.getForEntity(
                BASE_URL + "/auth/login", String.class);
        assertTrue(authResp.getStatusCode().is4xxClientError() || authResp.getStatusCode().is2xxSuccessful(),
                "Public /auth/login expected 2xx/4xx");
    }

    @Test
    @Order(18)
    void testImportEndpointPublic() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                BASE_URL + "/import", String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
                "/import should be accessible (public)");
    }

    @Test
    @Order(19)
    void testPublicWebhookEndpoint() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                BASE_URL + "/webhooks/shopify",
                "{\"test\":true}",
                String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
                "Public /webhooks should be accessible");
    }

    @Test
    @Order(20)
    void testPermissionResponseContainsPermissions() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        long ts = System.currentTimeMillis();
        String body = """
                {"username": "permcheck-%d", "password": "Test1234!", "role": "VIEWER"}
                """.formatted(ts);

        ResponseEntity<String> resp = restTemplate.exchange(
                BASE_URL + "/auth/register", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode json = objectMapper.readTree(resp.getBody());
        JsonNode data = json.get("data");
        assertTrue(data.has("permissions"), "AuthResponse should contain permissions field");
        assertNotNull(data.get("permissions"), "permissions should not be null");
        assertTrue(data.get("permissions").isArray(), "permissions should be an array");
    }

    @Test
    @Order(21)
    void testNewPathMappingsResolveCorrectly() {
        var token = tokens.get("ADMIN");
        assertNotNull(token);
        var headers = bearerHeaders(token);

        for (String endpoint : List.of("/invoicing", "/warehouses", "/shopify",
                "/integrations/bigcommerce", "/order-routing", "/integration-platform",
                "/integration-stores", "/integration-hub", "/api/ai",
                "/api/sample-data", "/email-parser", "/fulfillment")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    BASE_URL + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertFalse(resp.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN),
                    "New path " + endpoint + " should resolve to a resource (not 403 for ADMIN). Got: " + resp.getStatusCode());
        }
    }

    private static ResponseEntity<String> get(String path, HttpHeaders headers) {
        return restTemplate.exchange(BASE_URL + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private static ResponseEntity<String> post(String path, String body, HttpHeaders headers) {
        return restTemplate.exchange(BASE_URL + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private static ResponseEntity<String> delete(String path, HttpHeaders headers) {
        return restTemplate.exchange(BASE_URL + path, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private static HttpHeaders contentHeaders(HttpHeaders h) {
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
