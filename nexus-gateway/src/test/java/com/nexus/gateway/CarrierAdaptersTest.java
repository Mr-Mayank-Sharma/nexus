package com.nexus.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.gateway.account.Accounts;
import com.nexus.gateway.carrier.CarrierException;
import com.nexus.gateway.carrier.FedExCarrier;
import com.nexus.gateway.carrier.Shipping;
import com.nexus.gateway.carrier.UpsCarrier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FedEx and UPS adapters against a local server that answers like the carriers' APIs, checking what the adapters send
 * (OAuth, paths, key fields) and how they read the answers. The real sandboxes are the final check.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarrierAdaptersTest {

    static final ObjectMapper JSON = new ObjectMapper();
    HttpServer server;
    final Map<String, JsonNode> lastBody = new ConcurrentHashMap<>();
    final Map<String, String> lastAuth = new ConcurrentHashMap<>();

    @BeforeAll
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        route("/oauth/token", "{\"access_token\":\"fx-token\",\"expires_in\":3600}");
        route("/security/v1/oauth/token", "{\"access_token\":\"ups-token\",\"expires_in\":\"14399\"}");
        route("/ship/v1/shipments", """
                {"output":{"transactionShipments":[{"masterTrackingNumber":"794600000001","pieceResponses":[
                  {"trackingNumber":"794600000001","packageDocuments":[{"encodedLabel":"UERG","docType":"PDF"}]}],
                  "completedShipmentDetail":{"shipmentRating":{"shipmentRateDetails":[{"totalNetCharge":12.34}]}}}]}}""");
        route("/rate/v1/rates/quotes", """
                {"output":{"rateReplyDetails":[{"serviceType":"FEDEX_GROUND","serviceName":"FedEx Ground",
                  "ratedShipmentDetails":[{"totalNetCharge":11.5,"currency":"USD"}]}]}}""");
        route("/track/v1/trackingnumbers", """
                {"output":{"completeTrackResults":[{"trackResults":[{"latestStatusDetail":{"code":"OD","description":"On FedEx vehicle for delivery"},
                  "scanEvents":[{"date":"2026-10-22T08:00:00","eventType":"OD","eventDescription":"On vehicle","scanLocation":{"city":"Hoboken"}}]}]}]}}""");
        route("/ship/v1/shipments/cancel", "{\"output\":{\"cancelledShipment\":true}}");
        route("/api/shipments/v2409/ship", """
                {"ShipmentResponse":{"ShipmentResults":{"ShipmentIdentificationNumber":"1ZSHIP","ShipmentCharges":{"TotalCharges":{"CurrencyCode":"USD","MonetaryValue":"9.87"}},
                  "PackageResults":{"TrackingNumber":"1Z999AA10123456784","ShippingLabel":{"ImageFormat":{"Code":"GIF"},"GraphicImage":"R0lG"}}}}}""");
        route("/api/rating/v2409/Shop", """
                {"RateResponse":{"RatedShipment":[{"Service":{"Code":"03"},"TotalCharges":{"CurrencyCode":"USD","MonetaryValue":"10.10"}},
                  {"Service":{"Code":"01"},"TotalCharges":{"CurrencyCode":"USD","MonetaryValue":"40.00"},"GuaranteedDelivery":{"BusinessDaysInTransit":"1"}}]}}""");
        route("/api/track/v1/details/1Z999AA10123456784", """
                {"trackResponse":{"shipment":[{"package":[{"currentStatus":{"type":"D","description":"Delivered"},
                  "activity":[{"date":"20261022","time":"101500","status":{"code":"KB","description":"Delivered"},"location":{"address":{"city":"Hoboken"}}}]}]}]}}""");
        route("/api/addressvalidation/v2/3", """
                {"XAVResponse":{"AmbiguousAddressIndicator":"","Candidate":[{"AddressClassification":{"Code":"2"},
                  "AddressKeyFormat":{"AddressLine":["1 MAIN ST"],"PoliticalDivision2":"HOBOKEN","PoliticalDivision1":"NJ","PostcodePrimaryLow":"07030","CountryCode":"US"}}]}}""");
        server.createContext("/api/shipments/v2409/void/cancel/", ex -> {
            lastAuth.put("void", String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            byte[] b = "{\"VoidShipmentResponse\":{\"SummaryResult\":{\"Status\":{\"Code\":\"1\"}}}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/fail/oauth/token", ex -> {
            byte[] b = "{\"errors\":[{\"code\":\"NOT.AUTHORIZED.ERROR\",\"message\":\"The given client credentials were not valid.\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(401, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    void route(String path, String answer) {
        server.createContext(path, ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuth.put(path, String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            if (body.startsWith("{")) lastBody.put(path, JSON.readTree(body));
            else lastBody.put(path, JSON.createObjectNode().put("form", body));
            byte[] b = answer.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
    }

    @AfterAll
    void stop() {
        server.stop(0);
    }

    Accounts.Account account(String provider, String base) {
        return new Accounts.Account(provider + "-acct-" + base.hashCode(), provider, "SANDBOX", Map.of("accountNumber", "740561073", "baseUrl", base),
                Map.of("clientId", "cid", "clientSecret", "csecret"));
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    static final Shipping.Address FROM = new Shipping.Address("Warehouse", "Nexus", "2015550100", null, List.of("10 Dock Rd"), "Edison", "NJ", "08817", "US", false);
    static final Shipping.Address TO = new Shipping.Address("Ann Lee", null, "2015550111", "ann@example.com", List.of("1 Main St"), "Hoboken", "NJ", "07030", "US", true);
    static final List<Shipping.Parcel> PARCEL = List.of(new Shipping.Parcel(new BigDecimal("1.5"), null, null, null));

    @Test
    void fedexLabelRatesTrackAndVoid() {
        FedExCarrier fx = new FedExCarrier();
        Accounts.Account a = account("FEDEX", base());
        Shipping.Label l = fx.label(a, new Shipping.LabelRequest("FEDEX_GROUND", FROM, TO, PARCEL, "Order 10042", null, false));
        assertEquals("794600000001", l.masterTrackingNumber());
        assertEquals("UERG", l.packages().get(0).labelBase64());
        assertEquals(0, new BigDecimal("12.34").compareTo(l.cost()));
        assertTrue(lastBody.get("/oauth/token").path("form").asText().contains("grant_type=client_credentials"));
        assertEquals("Bearer fx-token", lastAuth.get("/ship/v1/shipments"));
        JsonNode sent = lastBody.get("/ship/v1/shipments");
        assertEquals("740561073", sent.at("/accountNumber/value").asText());
        assertEquals("07030", sent.at("/requestedShipment/recipients/0/address/postalCode").asText());
        assertEquals("KG", sent.at("/requestedShipment/requestedPackageLineItems/0/weight/units").asText());
        assertEquals("Order 10042", sent.at("/requestedShipment/requestedPackageLineItems/0/customerReferences/0/value").asText());

        fx.label(a, new Shipping.LabelRequest(null, TO, FROM, PARCEL, null, null, true));
        assertEquals("RETURN_SHIPMENT", lastBody.get("/ship/v1/shipments").at("/requestedShipment/shipmentSpecialServices/specialServiceTypes/0").asText());

        assertEquals(0, new BigDecimal("11.5").compareTo(fx.rates(a, new Shipping.RateRequest(FROM, TO, PARCEL)).get(0).amount()));
        Shipping.Tracking t = fx.track(a, "794600000001");
        assertEquals("OUT_FOR_DELIVERY", t.status());
        assertEquals("Hoboken", t.events().get(0).location());
        assertTrue(fx.voidLabel(a, "794600000001", "794600000001"));
    }

    @Test
    void upsLabelRatesTrackAddressAndVoid() {
        UpsCarrier ups = new UpsCarrier();
        Accounts.Account a = account("UPS", base());
        Shipping.Label l = ups.label(a, new Shipping.LabelRequest("03", FROM, TO, PARCEL, "Order 10042", null, false));
        assertEquals("1Z999AA10123456784", l.masterTrackingNumber());
        assertEquals("1ZSHIP", l.carrierShipmentId());
        assertEquals("R0lG", l.packages().get(0).labelBase64());
        assertTrue(lastAuth.get("/security/v1/oauth/token").startsWith("Basic "));
        JsonNode sent = lastBody.get("/api/shipments/v2409/ship");
        assertEquals("740561073", sent.at("/ShipmentRequest/Shipment/Shipper/ShipperNumber").asText());
        assertEquals("KGS", sent.at("/ShipmentRequest/Shipment/Package/0/PackageWeight/UnitOfMeasurement/Code").asText());
        assertEquals("1.5", sent.at("/ShipmentRequest/Shipment/Package/0/PackageWeight/Weight").asText());

        List<Shipping.Quote> q = ups.rates(a, new Shipping.RateRequest(FROM, TO, PARCEL));
        assertEquals("UPS Ground", q.get(0).serviceName());
        assertEquals(1, q.get(1).transitDays());
        assertEquals("DELIVERED", ups.track(a, "1Z999AA10123456784").status());
        Shipping.AddressCheck c = ups.validate(a, TO);
        assertTrue(c.ambiguous());
        assertFalse(c.valid());
        assertEquals(Boolean.TRUE, c.residential());
        assertEquals("HOBOKEN", c.suggested().city());
        assertTrue(ups.voidLabel(a, "1ZSHIP", "1Z999AA10123456784"));
        assertEquals("Bearer ups-token", lastAuth.get("void"));
    }

    @Test
    void aCarrierRefusalCarriesItsOwnMessage() {
        Accounts.Account a = account("FEDEX", base() + "/fail");
        CarrierException e = assertThrows(CarrierException.class, () -> new FedExCarrier().track(a, "1"));
        assertEquals(401, e.status());
        assertEquals("The given client credentials were not valid.", e.getMessage());
    }
}
