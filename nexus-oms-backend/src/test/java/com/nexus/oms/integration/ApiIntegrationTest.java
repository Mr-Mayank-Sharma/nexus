package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String viewerToken;
    private static String adminToken;

    @Test
    @Order(1)
    void testHealthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                BASE_URL + "/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(2)
    void testRegisterUsers() throws Exception {
        long ts = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String viewerBody = """
                {"username": "intviewer-%d", "password": "Test1234!", "role": "VIEWER"}
                """.formatted(ts);
        ResponseEntity<String> viewerResp = restTemplate.exchange(
                BASE_URL + "/auth/register", HttpMethod.POST,
                new HttpEntity<>(viewerBody, headers), String.class);
        assertEquals(HttpStatus.OK, viewerResp.getStatusCode());
        JsonNode viewerJson = objectMapper.readTree(viewerResp.getBody());
        viewerToken = viewerJson.get("data").get("accessToken").asText();

        String adminBody = """
                {"username": "intadmin-%d", "password": "Test1234!", "role": "ADMIN"}
                """.formatted(ts);
        ResponseEntity<String> adminResp = restTemplate.exchange(
                BASE_URL + "/auth/register", HttpMethod.POST,
                new HttpEntity<>(adminBody, headers), String.class);
        assertEquals(HttpStatus.OK, adminResp.getStatusCode());
        JsonNode adminJson = objectMapper.readTree(adminResp.getBody());
        adminToken = adminJson.get("data").get("accessToken").asText();

        assertNotNull(viewerToken);
        assertNotNull(adminToken);
    }

    @Test
    @Order(3)
    void testGetOrdersWithAdminAuth() {
        assertNotNull(adminToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(4)
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/orders", HttpMethod.POST,
                new HttpEntity<>(orderBody, headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertEquals(true, json.get("success").asBoolean());
    }

    @Test
    @Order(5)
    void testViewerCannotCreateOrder() {
        assertNotNull(viewerToken);

        String orderBody = """
                {"customerEmail": "blocked@test.com", "customerName": "Blocked", "shippingAddress": {"line1": "1"}, "channel": "WEB", "items": []}
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(viewerToken);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/orders", HttpMethod.POST,
                    new HttpEntity<>(orderBody, headers), String.class);
            fail("Expected 403 Forbidden");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    @Order(6)
    void testViewerCanReadOrders() {
        assertNotNull(viewerToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(viewerToken);

        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(7)
    void testViewerCannotAccessInventory() {
        assertNotNull(viewerToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(viewerToken);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/inventory", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            fail("Expected 403 Forbidden");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }
}
