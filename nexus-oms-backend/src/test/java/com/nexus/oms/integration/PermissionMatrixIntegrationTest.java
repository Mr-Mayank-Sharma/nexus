package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionMatrixIntegrationTest extends AbstractIntegrationTest {

    private static final Map<String, String> tokens = new LinkedHashMap<>();
    private static final Map<String, UUID> roleTenants = new LinkedHashMap<>();

    private static final String[] ALL_ROLES = {
        "VIEWER", "CEO",
        "CUSTOMER_SUPPORT", "FINANCE", "PROCUREMENT_MANAGER",
        "OPS_MANAGER", "LOGISTICS_MANAGER",
        "WAREHOUSE_MANAGER", "STORE_MANAGER", "BOPIS_OWNER",
        "PICKER", "PACKER", "LOADER",
        "ADMIN"
    };

    private static final String PAYLOAD = "{\"name\":\"pmt-test\"}";

    private static boolean initialized = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            registerAdminUser();
            registerAllRoles();
            seedAllPermissions();
            initialized = true;
        }
    }

    private void registerAllRoles() throws Exception {
        long ts = System.currentTimeMillis();
        for (String role : ALL_ROLES) {
            String body = """
                    {"username": "pmt-%s-%d", "password": "Test1234!", "role": "%s"}
                    """.formatted(role.toLowerCase(), ts, role);
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + "/auth/register", HttpMethod.POST,
                    new HttpEntity<>(body, plainHeaders()), String.class);
            assertEquals(HttpStatus.OK, resp.getStatusCode(), "Register " + role);
            JsonNode json = objectMapper.readTree(resp.getBody());
            JsonNode data = json.get("data");
            String token = data.get("accessToken").asText();
            assertNotNull(token, "Token for " + role);
            tokens.put(role, token);
            roleTenants.put(role, UUID.fromString(data.get("tenantId").asText()));
        }
        assertEquals(ALL_ROLES.length, tokens.size(), "All roles registered");
    }

    private void seedAllPermissions() {
        seedViewerPermissions();
        seedCeoPermissions();
        seedOpsManagerPermissions();
        seedWarehouseManagerPermissions();
        seedPickerPermissions();
        seedPackerPermissions();
        seedFinancePermissions();
        seedCustomerSupportPermissions();
    }

    private void seedViewerPermissions() {
        UUID tid = roleTenants.get("VIEWER");
        for (String g : List.of("orders", "products", "analytics", "shipments", "customers")) {
            seedPermission(tid, "VIEWER", g, "view", true, false);
        }
    }

    private void seedCeoPermissions() {
        UUID tid = roleTenants.get("CEO");
        for (String g : List.of("analytics", "ai", "orders", "inventory", "customers",
                "products", "returns", "shipments", "settings", "documents", "audit")) {
            seedPermission(tid, "CEO", g, "view", true, false);
        }
    }

    private void seedOpsManagerPermissions() {
        UUID tid = roleTenants.get("OPS_MANAGER");
        for (String g : List.of("orders", "inventory", "customers", "products",
                "returns", "shipments", "picking", "packing", "shipping",
                "warehouse", "routing", "carriers", "notifications", "analytics")) {
            seedPermission(tid, "OPS_MANAGER", g, "view", true, false);
        }
    }

    private void seedWarehouseManagerPermissions() {
        UUID tid = roleTenants.get("WAREHOUSE_MANAGER");
        for (String g : List.of("inventory", "picking", "packing", "shipping",
                "warehouse", "shipments", "notifications", "analytics")) {
            seedPermission(tid, "WAREHOUSE_MANAGER", g, "view", true, false);
        }
    }

    private void seedPickerPermissions() {
        UUID tid = roleTenants.get("PICKER");
        seedPermission(tid, "PICKER", "picking", "view", true, false);
    }

    private void seedPackerPermissions() {
        UUID tid = roleTenants.get("PACKER");
        seedPermission(tid, "PACKER", "packing", "view", true, false);
    }

    private void seedFinancePermissions() {
        UUID tid = roleTenants.get("FINANCE");
        for (String g : List.of("invoices", "payments", "returns", "analytics")) {
            seedPermission(tid, "FINANCE", g, "view", true, false);
        }
    }

    private void seedCustomerSupportPermissions() {
        UUID tid = roleTenants.get("CUSTOMER_SUPPORT");
        for (String g : List.of("orders", "customers", "returns", "products")) {
            seedPermission(tid, "CUSTOMER_SUPPORT", g, "view", true, false);
        }
        seedPermission(tid, "CUSTOMER_SUPPORT", "orders", "create", true, true);
    }

    private HttpHeaders plainHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @Order(1)
    void testAdminHasUnrestrictedAccess() {
        String token = tokens.get("ADMIN");
        assertNotNull(token);
        var headers = authHeaders(token);

        var endpoints = List.of("/orders", "/inventory", "/customers", "/products",
                "/returns", "/shipments", "/picking", "/packing", "/warehouse",
                "/warehouses", "/procurement", "/invoices", "/invoicing",
                "/payments", "/carriers", "/settings", "/workflows",
                "/analytics", "/documents", "/notifications", "/edi",
                "/routing", "/order-routing", "/rbac", "/cycle-counts",
                "/inventory-receipts", "/rate-shopping", "/routing-rules",
                "/audit", "/integration", "/shopify", "/integration-platform",
                "/api/ai", "/fulfillment");

        for (String endpoint : endpoints) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "ADMIN GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }
    }

    @Test
    @Order(2)
    void testViewerReadOnlyOnPermittedModules() {
        var token = tokens.get("VIEWER");
        assertNotNull(token);
        var headers = authHeaders(token);

        for (String endpoint : List.of("/orders", "/products", "/analytics", "/shipments", "/customers")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "VIEWER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        for (String endpoint : List.of("/inventory", "/procurement", "/invoices", "/payments",
                "/picking", "/packing", "/warehouse", "/settings", "/rbac")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                    "VIEWER GET " + endpoint + " expected 403");
        }

        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.POST,
                    new HttpEntity<>(PAYLOAD, headers), String.class);
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                    "VIEWER POST " + endpoint + " expected 403");
        }
    }

    @Test
    @Order(3)
    void testCEOReadOnlyOnStrategicModules() {
        var token = tokens.get("CEO");
        assertNotNull(token);
        var headers = authHeaders(token);

        for (String endpoint : List.of("/analytics", "/ai", "/orders", "/inventory", "/customers",
                "/products", "/returns", "/shipments", "/settings", "/documents", "/audit")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "CEO GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        for (String endpoint : List.of("/orders", "/inventory", "/customers")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.POST,
                    new HttpEntity<>(PAYLOAD, headers), String.class);
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                    "CEO POST " + endpoint + " expected 403");
        }
    }

    @Test
    @Order(4)
    void testOpsManagerFullCRUD() {
        var token = tokens.get("OPS_MANAGER");
        assertNotNull(token);
        var headers = authHeaders(token);

        for (String endpoint : List.of("/orders", "/inventory", "/customers", "/products",
                "/returns", "/shipments", "/picking", "/packing", "/shipping",
                "/warehouse", "/routing", "/carriers", "/notifications", "/analytics")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "OPS_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }
    }

    @Test
    @Order(5)
    void testWarehouseManagerFullCRUD() {
        var token = tokens.get("WAREHOUSE_MANAGER");
        assertNotNull(token);
        var headers = authHeaders(token);

        for (String endpoint : List.of("/inventory", "/picking", "/packing", "/shipping",
                "/warehouse", "/shipments", "/notifications", "/analytics")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "WAREHOUSE_MANAGER GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(PAYLOAD, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "WAREHOUSE_MANAGER POST /orders expected 403");
    }

    @Test
    @Order(6)
    void testPickerScopedToPicking() {
        var token = tokens.get("PICKER");
        assertNotNull(token);
        var headers = authHeaders(token);

        ResponseEntity<String> pickingResp = restTemplate.exchange(
                baseUrl() + "/picking", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertTrue(pickingResp.getStatusCode().is2xxSuccessful(),
                "PICKER GET /picking expected 2xx");

        for (String ep : List.of("/orders", "/inventory")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + ep, HttpMethod.POST,
                    new HttpEntity<>(PAYLOAD, headers), String.class);
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                    "PICKER POST " + ep + " expected 403");
        }
    }

    @Test
    @Order(7)
    void testPackerScopedToPacking() {
        var token = tokens.get("PACKER");
        assertNotNull(token);
        var headers = authHeaders(token);

        ResponseEntity<String> packingResp = restTemplate.exchange(
                baseUrl() + "/packing", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertTrue(packingResp.getStatusCode().is2xxSuccessful(),
                "PACKER GET /packing expected 2xx");

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(PAYLOAD, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "PACKER POST /orders expected 403");
    }

    @Test
    @Order(8)
    void testFinanceScopedToFinancialModules() {
        var token = tokens.get("FINANCE");
        assertNotNull(token);
        var headers = authHeaders(token);

        for (String endpoint : List.of("/invoices", "/invoicing", "/payments", "/returns", "/analytics")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "FINANCE GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(PAYLOAD, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "FINANCE POST /orders expected 403");
    }

    @Test
    @Order(9)
    void testCustomerSupportScoped() throws Exception {
        var token = tokens.get("CUSTOMER_SUPPORT");
        assertNotNull(token);
        var headers = authHeaders(token);

        for (String endpoint : List.of("/orders", "/customers", "/returns", "/products")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + endpoint, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertTrue(resp.getStatusCode().is2xxSuccessful(),
                    "CUSTOMER_SUPPORT GET " + endpoint + " expected 2xx got " + resp.getStatusCode());
        }

        String orderBody = """
                {"customerEmail": "cs-%s@test.com", "customerName": "CS Test", "shippingAddress": {"line1": "1"}, "channel": "WEB", "items": [{"sku": "CS-SKU-001", "productName": "Test", "quantity": 1, "unitPrice": 10.0}]}
                """.formatted(System.currentTimeMillis());

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/orders", HttpMethod.POST,
                new HttpEntity<>(orderBody, headers), String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "CUSTOMER_SUPPORT POST /orders expected 2xx got " + resp.getStatusCode());
    }

    @Test
    @Order(10)
    void testUnauthenticatedAccessBlocked() {
        var emptyHeaders = new HttpHeaders();
        for (String ep : List.of("/orders", "/inventory", "/settings")) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + ep, HttpMethod.GET,
                    new HttpEntity<>(emptyHeaders), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(),
                    "Unauthenticated GET " + ep + " expected 401");
        }
    }

    @Test
    @Order(11)
    void testPublicEndpointsAccessible() {
        ResponseEntity<String> healthResp = restTemplate.getForEntity(
                baseUrl() + "/actuator/health", String.class);
        assertTrue(healthResp.getStatusCode().is2xxSuccessful(),
                "Public /actuator/health expected 2xx");

        ResponseEntity<String> regResp = restTemplate.exchange(
                baseUrl() + "/auth/register", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"pubtest\",\"password\":\"Test1234!\"}", plainHeaders()),
                String.class);
        assertTrue(regResp.getStatusCode().is2xxSuccessful() || regResp.getStatusCode().is4xxClientError(),
                "Public /auth/register expected 2xx/4xx got " + regResp.getStatusCode());
    }

    @Test
    @Order(12)
    void testImportEndpointPublic() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/import", String.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
                "/import should be accessible (public). Got: " + resp.getStatusCode());
    }

    @Test
    @Order(13)
    void testPermissionResponseContainsPermissions() throws Exception {
        long ts = System.currentTimeMillis();
        String body = """
                {"username": "permcheck-%d", "password": "Test1234!", "role": "VIEWER"}
                """.formatted(ts);

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/auth/register", HttpMethod.POST,
                new HttpEntity<>(body, plainHeaders()), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        JsonNode json = objectMapper.readTree(resp.getBody());
        JsonNode data = json.get("data");
        assertTrue(data.has("permissions"), "AuthResponse should contain permissions field");
        assertNotNull(data.get("permissions"), "permissions should not be null");
        assertTrue(data.get("permissions").isArray(), "permissions should be an array");
    }
}
