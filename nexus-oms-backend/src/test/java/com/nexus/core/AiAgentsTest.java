package com.nexus.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.ai.ExceptionAgent;
import com.nexus.core.ai.ProposalService;
import com.nexus.core.ai.RoutingDrafter;
import com.nexus.core.ai.ScriptedProvider;
import com.nexus.core.ai.ScriptedProvider.Turn;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.RoutingSetupService.Cond;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The exception agent and plain-language routing with a scripted model: what they read, what they propose, what a person approves. */
@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiAgentsTest {

    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired ExceptionAgent exceptions;
    @Autowired RoutingDrafter drafter;
    @Autowired ProposalService proposals;
    @Autowired RoutingService routing;
    @Autowired com.nexus.core.service.RoutingSetupService routingSetup;
    @Autowired OrderService orders;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired ProductStoreService stores;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String store = "AGENT-STORE", empty, full, hat, sku = "AGENT-HAT-1";

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            stores.createIfMissing(new ProductStoreService.NewStore(store, store, "USD", null, "WEB_SALES_CHANNEL", null, null));
            empty = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Agent Empty Store", null, null, null, null, null, null, null, null, null));
            full = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Agent Full Store", null, null, null, null, null, null, null, null, null));
            hat = products.create(new ProductService.NewProduct(null, "Agent Hat", null, null, null, null, null, false, false));
            products.setIdentification(hat, "SKU", sku);
            inventory.receive(hat, empty, new BigDecimal("1"), null, null, null, null);
            inventory.receive(hat, full, new BigDecimal("9"), null, null, null, null);
        });
    }

    private String approved() {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, store, "WEB_SALES_CHANNEL", "USD", null, null, null, null, null, null, null,
                List.of(new OrderService.NewItem(hat, "Hat", BigDecimal.ONE, new BigDecimal("30"), null, null, 0)), null, null, null, null, null, null, "t")));
        tx.executeWithoutResult(s -> orders.changeStatus(o, "ORDER_APPROVED", null, "t"));
        return o;
    }

    @Test
    void theExceptionAgentProposesAMoveThatAPersonApproves() {
        String o = approved();
        routing.reallocate(o, "00001", empty, "routed earlier", "t");
        jdbc.update("UPDATE order_header SET order_date = ? WHERE order_id = ?", java.sql.Timestamp.valueOf(com.nexus.core.service.CoreClock.now().minusDays(3)), o);
        ScriptedProvider model = new ScriptedProvider(List.of(
                Turn.tools("stuck_orders", "{\"olderThanHours\":24,\"limit\":100}", "stock_lookup", "{\"sku\":\"" + sku + "\"}"),
                Turn.tools("propose_reallocate", "{\"orderId\":\"" + o + "\",\"shipGroupSeqId\":\"00001\",\"facilityId\":\"" + full + "\",\"reason\":\"Empty store has 0 left; Full store has 9\"}"),
                Turn.answer(m -> "Order " + o + " was stuck at Agent Empty Store; I proposed moving it to Agent Full Store.")));
        ExceptionAgent.Outcome out = exceptions.run("Find stuck orders and propose fixes", "manager", model);
        assertTrue(out.run().grounded());
        assertEquals(1, out.proposals().size());
        ProposalService.View p = out.proposals().get(0);
        assertEquals("AIP_PENDING", p.statusId(), "a move waits for a person");
        assertEquals(out.run().runId(), jdbc.queryForObject("SELECT ai_run_id FROM ai_proposal WHERE proposal_id = ?", String.class, p.proposalId()), "tied to the run that made it");
        assertEquals(empty, jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, o), "nothing moved yet");
        String seen = model.requests().get(1).messages().toString();
        assertTrue(seen.contains(o) && seen.contains("Agent Full Store"), "it saw the stuck order and where the stock is");

        assertEquals("AIP_APPLIED", proposals.approve(p.proposalId(), "manager").statusId());
        assertEquals(full, jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, o));
    }

    @Test
    void plainLanguageRoutingDraftsSimulatesAndWaitsForApproval() throws Exception {
        String live = routingSetup.createGroup(store, "Live", 15L);
        String r = routingSetup.addRouting(live, "Everything", 1, List.of(Cond.filter("facilityId", "in", RoutingService.QUEUE)));
        routingSetup.addRule(r, "Empty store first", 1, false, List.of(Cond.filter("facilityId", "in", empty)), Map.of("ORA_MV_TO_QUEUE", "UNFILLABLE_PARKING"));
        routingSetup.activate(r);
        String o1 = approved(), o2 = approved();

        String ruleSet = """
                {"name": "Full store first", "routings": [{"name": "Everything", "orderConditions": [{"field": "facilityId", "operator": "in", "value": "_NA_"}],
                  "rules": [{"name": "Most stock first", "locationConditions": [{"sort": true, "field": "inventory", "operator": "desc"}],
                             "actions": [{"type": "ORA_MV_TO_QUEUE", "value": "UNFILLABLE_PARKING"}]}]}]}""";
        ScriptedProvider model = new ScriptedProvider(List.of(
                Turn.tools("current_routing", "{\"productStoreId\":\"" + store + "\"}", "list_locations", "{}"),
                Turn.tools("draft_and_simulate", ruleSet),
                Turn.toolsFrom(m -> new String[]{"propose_activation", "{\"draftGroupId\":\"" + draftId(m) + "\",\"reason\":\"Routes both orders instead of one\"}"}),
                Turn.answer(m -> "Drafted 'Full store first'; it routes 2 orders where the live rules route 1. Waiting for approval.")));
        RoutingDrafter.Outcome out = drafter.draft(store, "Send orders to the store with the most stock", "manager", model);

        String draft = out.draftGroupIds().get(0);
        JsonNode sim = JSON.readTree(model.requests().get(2).messages().get(model.requests().get(2).messages().size() - 1).at("/content/0/content").asText());
        assertTrue(sim.at("/draft/routed").asInt() >= 2, sim.toString());
        assertTrue(sim.at("/live/routed").asInt() < sim.at("/draft/routed").asInt(), "the live rules park what the empty store can't fill: " + sim);
        assertNull(jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, o1), "simulations change nothing");
        assertNull(jdbc.queryForObject("SELECT schedule_minutes FROM order_routing_group WHERE routing_group_id = ?", Long.class, draft), "the draft does not run");

        ProposalService.View p = out.proposals().get(0);
        assertEquals("ACTIVATE_ROUTING", p.actionType());
        assertEquals("AIP_PENDING", p.statusId());
        assertEquals("AIP_APPLIED", proposals.approve(p.proposalId(), "manager").statusId());
        assertEquals(15L, jdbc.queryForObject("SELECT schedule_minutes FROM order_routing_group WHERE routing_group_id = ?", Long.class, draft));
        assertNull(jdbc.queryForObject("SELECT schedule_minutes FROM order_routing_group WHERE routing_group_id = ?", Long.class, live));
        assertEquals("AIP_UNDONE", proposals.undo(p.proposalId(), "manager").statusId());
        assertEquals(15L, jdbc.queryForObject("SELECT schedule_minutes FROM order_routing_group WHERE routing_group_id = ?", Long.class, live), "undo puts the live rules back");
        assertNull(jdbc.queryForObject("SELECT schedule_minutes FROM order_routing_group WHERE routing_group_id = ?", Long.class, draft));
        assertTrue(o2 != null);
    }

    /** The draft group id from the last draft_and_simulate result in the conversation. */
    static String draftId(com.fasterxml.jackson.databind.node.ArrayNode messages) {
        try {
            for (int i = messages.size() - 1; i >= 0; i--) {
                for (JsonNode b : messages.get(i).path("content")) {
                    if (!"tool_result".equals(b.path("type").asText())) continue;
                    JsonNode n = JSON.readTree(b.path("content").asText());
                    if (n.has("draftGroupId")) return n.path("draftGroupId").asText();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("no draft yet");
    }
}
