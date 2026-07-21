package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalyticsControllerIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            registerAdminUser();
        }
    }

    private void assertSuccessResponse(ResponseEntity<String> response) throws Exception {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertNotNull(json.get("data"));
    }

    @Test
    @Order(1)
    void getAnalytics_ReturnsKPIs() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(2)
    void getDashboard_ReturnsKPIs() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/dashboard", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(3)
    void getActivity_ReturnsList() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/activity", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(4)
    void getOrderVelocity_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/orders/velocity", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(5)
    void getAlerts_ReturnsList() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/alerts", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(6)
    void getOrderStatusDistribution_ReturnsList() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/order-status-distribution", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(7)
    void getTaskQueueSummary_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/task-queue-summary", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(8)
    void getCarrierPerformance_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/carrier-performance", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("data").has("totalShipments"));
        assertTrue(json.get("data").has("onTimeRate"));
    }

    @Test
    @Order(9)
    void getExceptions_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/exceptions", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);
    }

    @Test
    @Order(10)
    void getCostBreakdown_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/cost-breakdown", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("data").has("shipping"));
        assertTrue(json.get("data").has("labor"));
    }

    @Test
    @Order(11)
    void getLanePerformance_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/lanes", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("data").has("lanes"));
        assertTrue(json.get("data").get("lanes").size() >= 1);
    }

    @Test
    @Order(12)
    void getReturnsAnalytics_ReturnsData() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics/returns", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertSuccessResponse(response);

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("data").has("totalReturns"));
        assertTrue(json.get("data").has("topReasons"));
    }

    @Test
    @Order(13)
    void analyticsEndpoints_Unauthenticated_Returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(14)
    void viewerUser_CannotAccessAnalytics() throws Exception {
        if (adminToken == null) {
            registerAdminUser();
        }
        long ts = System.currentTimeMillis();
        String body = """
                {"username": "analytics-viewer-%d", "password": "Test1234!", "role": "VIEWER"}
                """.formatted(ts);

        HttpHeaders regHeaders = new HttpHeaders();
        regHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> regResp = restTemplate.postForEntity(
                baseUrl() + "/auth/register",
                new HttpEntity<>(body, regHeaders),
                String.class);
        assertEquals(HttpStatus.OK, regResp.getStatusCode());

        com.fasterxml.jackson.databind.JsonNode regJson = objectMapper.readTree(regResp.getBody());
        String viewerToken = regJson.get("data").get("accessToken").asText();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/analytics", HttpMethod.GET,
                new HttpEntity<>(authHeaders(viewerToken)), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
