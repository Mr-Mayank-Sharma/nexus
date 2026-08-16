package com.nexus.oms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            registerAdminUser();
        }
    }

    @Test
    @Order(1)
    void login_WithValidCredentials_ReturnsAccessAndRefreshToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"username": "%s", "password": "Test1234!"}
                """.formatted(username());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        JsonNode data = json.get("data");
        assertTrue(data.has("accessToken"));
        assertTrue(data.has("refreshToken"));
    }

    @Test
    @Order(2)
    void login_WithInvalidPassword_Returns401() throws Exception {
        String body = """
                {"username": "%s", "password": "wrong-password"}
                """.formatted(username());

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl() + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build();

        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode());
    }

    @Test
    @Order(3)
    void mfaVerify_WithUnknownSession_ReturnsBadRequest() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"mfaToken": "does-not-exist", "totpCode": "123456"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/mfa/verify", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(4)
    void refresh_WithInvalidToken_ReturnsBadRequest() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"refreshToken": "not-a-real-token"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(5)
    void ssoProviders_ReturnsConfiguredProviders() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/sso/providers", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(6)
    void ssoLogin_WithUnsupportedProvider_ReturnsBadRequest() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"idToken": "dummy-token"}
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/sso/unsupported-provider", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(7)
    void tenants_IsPubliclyReadable() throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/auth/tenants", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("data").isArray());
    }

    private String username() {
        return adminUsername;
    }
}
