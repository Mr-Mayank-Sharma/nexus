package com.nexus.oms.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.CoreTestData;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import com.nexus.core.service.ShipmentService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** The OMS side of the gateway against a local fake gateway: what it sends, what it keeps, and that the key is encrypted. */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayServiceTest {

    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired GatewayService gateway;
    @Autowired OrderService orders;
    @Autowired ShipmentService shipments;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired ProductStoreService stores;
    @Autowired PartyService parties;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    HttpServer fake;
    final Map<String, JsonNode> sent = new ConcurrentHashMap<>();
    final Map<String, String> keys = new ConcurrentHashMap<>();
    String store = "GW-STORE", product, facility, customer, shipTo;

    @BeforeAll
    void setUp() throws Exception {
        CoreTestData.ensureSeeded(jdbc);
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/v1/labels", ex -> {
            sent.put("label", JSON.readTree(ex.getRequestBody().readAllBytes()));
            keys.put("label", String.valueOf(ex.getRequestHeaders().getFirst("X-Nexus-Gateway-Key")));
            reply(ex, 200, "{\"carrier\":\"UPS\",\"carrierShipmentId\":\"1ZS\",\"masterTrackingNumber\":\"1Z1\",\"labels\":["
                    + "{\"labelId\":\"L1\",\"trackingNumber\":\"1Z1\",\"format\":\"GIF\"},{\"labelId\":\"L2\",\"trackingNumber\":\"1Z2\",\"format\":\"GIF\"}],"
                    + "\"cost\":14.20,\"currency\":\"USD\"}");
        });
        fake.createContext("/v1/email/send", ex -> {
            sent.put("email", JSON.readTree(ex.getRequestBody().readAllBytes()));
            reply(ex, 409, "{\"error\":\"No email template 'NOPE' is set up\"}");
        });
        fake.start();
        tx.executeWithoutResult(s -> {
            stores.createIfMissing(new ProductStoreService.NewStore(store, "Gateway store", "USD", null, "WEB_SALES_CHANNEL", null, null));
            product = products.create(new ProductService.NewProduct(null, "Gateway Mug", null, null, null, null, null, false, false));
            facility = facilities.create(new FacilityService.NewFacility(null, "WAREHOUSE", "Gateway WH", null, null, "08817", null, null, null, null, null, null));
            inventory.receive(product, facility, new BigDecimal("5"), null, null, null, null);
            customer = parties.createPerson(PartyService.PersonName.parse("Gia Tran"), null);
            shipTo = parties.addPostalAddress(customer, new PartyService.AddressInput("Gia Tran", "7 Bloom St", null, "Hoboken", "NJ", "07030", "US",
                    null, null, true), "SHIPPING_LOCATION");
        });
        gateway.configure(store, "http://127.0.0.1:" + fake.getAddress().getPort(), "ngw_secret_key", "ups");
    }

    static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @AfterAll
    void stop() {
        fake.stop(0);
    }

    @Test
    void labelsAShipmentAndKeepsTrackingPerBox() {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, store, "WEB_SALES_CHANNEL", "USD", null, null, null, customer, null, null,
                List.of(new OrderService.NewShipGroup("STANDARD", facility, shipTo, null, null, true, false, null, null, null, null)),
                List.of(new OrderService.NewItem(product, "Mug", new BigDecimal("2"), BigDecimal.TEN, null, null, 0)), null, null, null, null,
                "ORDER_APPROVED", null, "t")));
        String sid = tx.execute(s -> {
            orders.reserve(o, "00001", facility, null, "t");
            String id = shipments.createSalesShipment(o, "00001", null, "t");
            shipments.addPackage(id, "BOX_SMALL", new BigDecimal("0.6"), null, Map.of("00001", BigDecimal.ONE));
            shipments.addPackage(id, "BOX_SMALL", new BigDecimal("0.7"), null, Map.of("00001", BigDecimal.ONE));
            return id;
        });
        GatewayService.LabelOutcome out = gateway.label(sid, null, "03", "t");
        assertEquals("UPS", out.carrier(), "the store's default carrier");
        assertEquals("ngw_secret_key", keys.get("label"));
        JsonNode req = sent.get("label");
        assertEquals(store, req.path("storeId").asText());
        assertEquals("07030", req.at("/to/postalCode").asText());
        assertEquals("NJ", req.at("/to/state").asText());
        assertEquals("US", req.at("/to/countryCode").asText());
        assertEquals("08817", req.at("/from/postalCode").asText());
        assertEquals(2, req.path("parcels").size());
        assertEquals(0.7, req.at("/parcels/1/weightKg").asDouble(), 0.0001);

        assertEquals(List.of("1Z1", "1Z2"), jdbc.queryForList("SELECT tracking_code FROM shipment_package_route_seg WHERE shipment_id = ? ORDER BY shipment_package_seq_id", String.class, sid));
        assertTrue(jdbc.queryForObject("SELECT label_url FROM shipment_package_route_seg WHERE shipment_id = ? AND shipment_package_seq_id = '00002'", String.class, sid).endsWith("/v1/labels/L2"));
        assertEquals("UPS", jdbc.queryForObject("SELECT carrier_party_id FROM shipment_route_segment WHERE shipment_id = ?", String.class, sid));

        tx.executeWithoutResult(s -> shipments.ship(sid, null, null, null, null, "t"));
        assertEquals("1Z1", jdbc.queryForObject("SELECT tracking_number FROM order_item_ship_group WHERE order_id = ?", String.class, o), "shipping keeps the label's tracking");
        assertEquals("ORDER_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, o));
    }

    @Test
    void theKeyIsStoredEncryptedAndGatewayErrorsAreReadable() {
        String stored = jdbc.queryForObject("SELECT setting_value FROM product_store_setting WHERE product_store_id = ? AND setting_type_enum_id = 'GATEWAY_API_KEY'", String.class, store);
        assertFalse(stored.contains("ngw_secret_key"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> gateway.email(store, "NOPE", "a@example.com", Map.of()));
        assertEquals("The gateway said: No email template 'NOPE' is set up", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> gateway.configure(store, "http://gateway.example.com", null, null), "https only, except localhost");
    }
}
