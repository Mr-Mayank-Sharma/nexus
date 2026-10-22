package com.nexus.oms.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.CoreTestData;
import com.nexus.core.service.AgentKeyService;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** An AI shopping agent's whole journey over HTTP, with security filters on: key, checkout, order, and changes after purchase. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentCommerceApiTest {

    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired MockMvc mvc;
    @Autowired AgentKeyService agentKeys;
    @Autowired ProductStoreService stores;
    @Autowired ProductService products;
    @Autowired FacilityService facilities;
    @Autowired InventoryService inventory;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String store = "AGENT-SHOP", key, product;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            stores.createIfMissing(new ProductStoreService.NewStore(store, "Agent shop", "USD", null, "WEB_SALES_CHANNEL", null, null));
            stores.setSetting(store, "AGENT_SHIPPING_NEXT_DAY", "12");
            stores.setSetting(store, "AGENT_TAX_PCT", "10");
            product = products.create(new ProductService.NewProduct(null, "Agent Scarf", null, null, null, null, null, false, false));
            products.setIdentification(product, "SKU", "SCARF-RED");
            products.setPrice(product, "DEFAULT_PRICE", "USD", null, new BigDecimal("40.00"));
            String dc = facilities.create(new FacilityService.NewFacility(null, "DISTRIBUTION_CENTER", "Agent DC", null, null, null, null, null, null, null, null, null));
            inventory.receive(product, dc, new BigDecimal("5"), null, null, null, null);
            key = agentKeys.issueKey(store);
        });
    }

    JsonNode call(String method, String path, String body, String idempotency) throws Exception {
        var req = ("GET".equals(method) ? get(path) : post(path)).header("Authorization", "Bearer " + key).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(body);
        if (idempotency != null) req.header("Idempotency-Key", idempotency);
        MvcResult r = mvc.perform(req).andReturn();
        JsonNode n = JSON.readTree(r.getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) n).put("_http", r.getResponse().getStatus());
        return n;
    }

    static final String CHECKOUT = """
            {"items": [{"id": "SCARF-RED", "quantity": 2}],
             "buyer": {"first_name": "Ivy", "last_name": "Agentson", "email": "ivy@example.com"},
             "fulfillment_address": {"name": "Ivy Agentson", "line_one": "3 Elm St", "city": "Hoboken", "state": "NJ", "country": "US", "postal_code": "07030"}}""";

    @Test
    void anAgentChecksOutAndTheOrderIsPlaced() throws Exception {
        String createKey = "create-" + java.util.UUID.randomUUID(), payKey = "pay-" + java.util.UUID.randomUUID();
        assertEquals(401, mvc.perform(post("/agent-commerce/checkout_sessions").contentType(MediaType.APPLICATION_JSON).content(CHECKOUT)).andReturn().getResponse().getStatus());

        JsonNode s = call("POST", "/agent-commerce/checkout_sessions", CHECKOUT, createKey);
        assertEquals(201, s.path("_http").asInt(), s.toString());
        assertEquals("not_ready_for_payment", s.path("status").asText(), "no delivery option chosen yet");
        assertEquals(8000, s.at("/line_items/0/subtotal").asLong(), "2 x 40.00 in cents");
        assertEquals(800, s.at("/line_items/0/tax").asLong());
        assertEquals(2, s.path("fulfillment_options").size());
        String id = s.path("id").asText();
        assertEquals(id, call("POST", "/agent-commerce/checkout_sessions", CHECKOUT, createKey).path("id").asText(), "the same key gives the same session");

        JsonNode ready = call("POST", "/agent-commerce/checkout_sessions/" + id, "{\"fulfillment_option_id\": \"NEXT_DAY\"}", null);
        assertEquals("ready_for_payment", ready.path("status").asText(), ready.toString());
        long total = 0;
        for (JsonNode t : ready.path("totals")) if (t.path("type").asText().equals("total")) total = t.path("amount").asLong();
        assertEquals(8000 + 800 + 1200 + 120, total, "items, their tax, next-day delivery and its tax");

        JsonNode done = call("POST", "/agent-commerce/checkout_sessions/" + id + "/complete", "{\"payment_data\": {\"token\": \"spt_123secret\", \"provider\": \"stripe\"}}", payKey);
        assertEquals("completed", done.path("status").asText(), done.toString());
        String orderId = done.at("/order/id").asText();
        assertEquals(orderId, call("POST", "/agent-commerce/checkout_sessions/" + id + "/complete", "{\"payment_data\": {\"token\": \"spt_123secret\"}}", payKey)
                .at("/order/id").asText(), "completing twice with the same key is safe");
        assertEquals("AGENT_SALES_CHANNEL", jdbc.queryForObject("SELECT sales_channel_enum_id FROM order_header WHERE order_id = ?", String.class, orderId));
        assertEquals(0, new BigDecimal("101.20").compareTo(jdbc.queryForObject("SELECT grand_total FROM order_header WHERE order_id = ?", BigDecimal.class, orderId)));
        String ref = jdbc.queryForObject("SELECT manual_ref_num FROM order_payment_preference WHERE order_id = ?", String.class, orderId);
        assertTrue(ref.startsWith("stripe:") && !ref.contains("spt_123secret"), "the token is kept only by reference");
        assertEquals("created", call("GET", "/agent-commerce/orders/" + orderId, null, null).path("status").asText());

        JsonNode moved = call("POST", "/agent-commerce/orders/" + orderId + "/address",
                "{\"fulfillment_address\": {\"name\": \"Ivy\", \"line_one\": \"9 Oak Ave\", \"city\": \"Jersey City\", \"state\": \"NJ\", \"country\": \"US\", \"postal_code\": \"07302\"}}", null);
        assertEquals("done", moved.path("status").asText(), moved.toString());
        assertEquals("07302", jdbc.queryForObject("SELECT pa.postal_code FROM order_item_ship_group g JOIN postal_address pa ON pa.contact_mech_id = g.contact_mech_id "
                + "WHERE g.order_id = ?", String.class, orderId));
        assertEquals("done", call("POST", "/agent-commerce/orders/" + orderId + "/cancel", "{\"reason\": \"changed my mind\"}", null).path("status").asText());
        assertEquals("ORDER_CANCELLED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, orderId));
    }

    @Test
    void outsideThePolicyAPersonDecidesAndBadInputIsExplained() throws Exception {
        JsonNode s = call("POST", "/agent-commerce/checkout_sessions", CHECKOUT.replace("\"quantity\": 2", "\"quantity\": 1"), null);
        String id = s.path("id").asText();
        call("POST", "/agent-commerce/checkout_sessions/" + id, "{\"fulfillment_option_id\": \"STANDARD\"}", null);
        String orderId = call("POST", "/agent-commerce/checkout_sessions/" + id + "/complete", "{\"payment_data\": {\"token\": \"spt_x\"}}", null).at("/order/id").asText();
        jdbc.update("UPDATE order_header SET order_date = ? WHERE order_id = ?", java.sql.Timestamp.valueOf(com.nexus.core.service.CoreClock.now().minusDays(2)), orderId);
        JsonNode late = call("POST", "/agent-commerce/orders/" + orderId + "/cancel", "{}", null);
        assertEquals("needs_review", late.path("status").asText(), late.toString());
        assertEquals(1, late.path("review_ids").size());
        assertEquals("AIP_PENDING", jdbc.queryForObject("SELECT status_id FROM ai_proposal WHERE proposal_id = ?", String.class, late.at("/review_ids/0").asText()));

        JsonNode tooMany = call("POST", "/agent-commerce/checkout_sessions", CHECKOUT.replace("\"quantity\": 2", "\"quantity\": 50"), null);
        assertTrue(tooMany.toString().contains("out_of_stock"), tooMany.toString());
        JsonNode unknown = call("POST", "/agent-commerce/checkout_sessions", CHECKOUT.replace("SCARF-RED", "NOPE"), null);
        assertTrue(unknown.toString().contains("Unknown item NOPE"));
        JsonNode notReady = call("POST", "/agent-commerce/checkout_sessions/" + unknown.path("id").asText() + "/complete", "{\"payment_data\": {\"token\": \"t\"}}", null);
        assertEquals(409, notReady.path("_http").asInt());
    }
}
