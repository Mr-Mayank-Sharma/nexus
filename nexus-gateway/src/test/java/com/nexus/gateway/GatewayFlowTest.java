package com.nexus.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The whole gateway over HTTP: an operator sets up a client, the client ships and emails with the test providers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayFlowTest {

    static final String ADMIN = "test-admin-token-0123456789";
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    String clientId, key;

    record Res(int status, JsonNode body) {
    }

    Res call(String method, String path, String header, String value, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (header != null) b.header(header, value);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(r.statusCode(), r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body()));
    }

    Res admin(String method, String path, Object body) throws Exception {
        return call(method, path, "X-Admin-Token", ADMIN, body);
    }

    Res client(String method, String path, Object body) throws Exception {
        return call(method, path, "X-Nexus-Gateway-Key", key, body);
    }

    static Map<String, Object> address(String name, String zip) {
        return Map.of("name", name, "lines", java.util.List.of("1 Main St"), "city", "Hoboken", "state", "NJ", "postalCode", zip, "countryCode", "US");
    }

    @BeforeAll
    void setUp() throws Exception {
        Res c = admin("POST", "/admin/clients", Map.of("name", "Test retailer"));
        assertEquals(200, c.status());
        clientId = c.body().path("clientId").asText();
        key = c.body().path("apiKey").asText();
        assertTrue(key.startsWith("ngw_"));
        assertEquals(200, admin("PUT", "/admin/clients/" + clientId + "/accounts", Map.of("provider", "NEXUS_TEST", "secrets", Map.of("token", "s3cret"))).status());
        assertEquals(200, admin("PUT", "/admin/clients/" + clientId + "/accounts", Map.of("provider", "TEST_EMAIL")).status());
        assertEquals(200, admin("PUT", "/admin/clients/" + clientId + "/templates", Map.of("templateId", "PICKUP_READY",
                "subject", "Your order {{orderName}} is ready", "bodyHtml", "<p>Hi {{firstName}}, collect it at {{storeName}}.</p>")).status());
    }

    @Test
    void keysAndTheAdminTokenAreRequired() throws Exception {
        assertEquals(401, call("GET", "/v1/carriers", null, null, null).status());
        assertEquals(401, call("GET", "/v1/carriers", "X-Nexus-Gateway-Key", "ngw_wrong", null).status());
        assertEquals(401, call("POST", "/admin/clients", "X-Admin-Token", "nope", Map.of("name", "x")).status());
        assertEquals(200, client("GET", "/v1/carriers", null).status());
        JsonNode accounts = admin("GET", "/admin/clients/" + clientId + "/accounts", null).body();
        assertFalse(accounts.toString().contains("s3cret"), "secret values are never returned");
        assertTrue(accounts.toString().contains("\"token\""), "only the names of secrets");
        String stored = jdbc.queryForObject("SELECT secret FROM provider_account WHERE client_id = ? AND provider = 'NEXUS_TEST' AND active = TRUE", String.class, clientId);
        assertTrue(stored.startsWith("v1:") && !stored.contains("s3cret"), "stored encrypted");
    }

    @Test
    void ratesLabelsReprintVoidAddressAndTracking() throws Exception {
        Map<String, Object> parcel = Map.of("weightKg", 2);
        Res rates = client("POST", "/v1/rates", Map.of("from", address("WH", "07030"), "to", address("Ann", "08817"), "parcels", java.util.List.of(parcel)));
        assertEquals(200, rates.status());
        assertEquals("NEXUS_TEST", rates.body().get(0).path("carrier").asText());
        assertEquals(8.0, rates.body().get(0).path("amount").asDouble(), 0.001, "5 + 2 kg x 1.5, cheapest first");

        Res label = client("POST", "/v1/labels", Map.of("storeId", "SHOP-1", "carrier", "nexus_test", "service", "STANDARD", "from", address("WH", "07030"),
                "to", address("Ann", "08817"), "parcels", java.util.List.of(parcel, parcel), "reference", "Order 10042"));
        assertEquals(200, label.status(), label.body().toString());
        assertEquals(2, label.body().path("labels").size());
        String labelId = label.body().at("/labels/0/labelId").asText();
        String tracking = label.body().at("/labels/0/trackingNumber").asText();
        Res doc = client("GET", "/v1/labels/" + labelId, null);
        assertTrue(new String(Base64.getDecoder().decode(doc.body().path("contentBase64").asText())).startsWith("TEST LABEL " + tracking));

        assertTrue(client("POST", "/v1/labels/void", Map.of("carrier", "NEXUS_TEST", "trackingNumber", tracking)).body().path("voided").asBoolean());
        assertTrue(client("GET", "/v1/labels/" + labelId, null).body().path("voided").asBoolean());

        Res bad = client("POST", "/v1/addresses/validate", Map.of("carrier", "NEXUS_TEST", "address", Map.of("lines", java.util.List.of(""), "countryCode", "USA")));
        assertFalse(bad.body().path("valid").asBoolean());
        assertEquals(3, bad.body().path("messages").size());

        Res track = client("GET", "/v1/tracking/NEXUS_TEST/" + tracking, null);
        assertTrue(java.util.Set.of("LABEL_CREATED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED").contains(track.body().path("status").asText()));

        Res noAccount = client("POST", "/v1/labels", Map.of("carrier", "FEDEX", "from", address("WH", "07030"), "to", address("Ann", "08817"),
                "parcels", java.util.List.of(parcel)));
        assertEquals(409, noAccount.status());
        assertTrue(noAccount.body().path("error").asText().contains("No FEDEX account"));
        Res unknown = client("POST", "/v1/labels", Map.of("carrier", "PIGEON", "from", address("WH", "07030"), "to", address("Ann", "08817"),
                "parcels", java.util.List.of(parcel)));
        assertEquals(400, unknown.status());
    }

    @Test
    void emailRendersTheStoreTemplateEscapesDataAndLogsEveryCall() throws Exception {
        admin("PUT", "/admin/clients/" + clientId + "/templates", Map.of("storeId", "SHOP-2", "templateId", "PICKUP_READY",
                "subject", "Ready at {{storeName}}", "bodyHtml", "<p>{{firstName}}, see you at {{storeName}}.</p>"));
        Res sent = client("POST", "/v1/email/send", Map.of("storeId", "SHOP-2", "templateId", "PICKUP_READY", "to", "ann@example.com",
                "data", Map.of("firstName", "<b>Ann</b>", "storeName", "Hoboken")));
        assertEquals(200, sent.status(), sent.body().toString());
        JsonNode outbox = admin("GET", "/admin/clients/" + clientId + "/test-outbox", null).body();
        JsonNode last = outbox.get(outbox.size() - 1);
        assertEquals("Ready at Hoboken", last.path("subject").asText(), "the store's own template wins");
        assertEquals("<p>&lt;b&gt;Ann&lt;/b&gt;, see you at Hoboken.</p>", last.path("html").asText(), "data can't inject markup");

        assertEquals(400, client("POST", "/v1/email/send", Map.of("templateId", "PICKUP_READY", "to", "not-an-email", "data", Map.of())).status());
        JsonNode log = client("GET", "/v1/requests?limit=100", null).body();
        assertTrue(log.toString().contains("\"operation\":\"EMAIL\"") || log.toString().contains("\"OPERATION\":\"EMAIL\""), log.toString());
        assertEquals(200, admin("GET", "/admin/health?hours=1", null).status());
    }
}
