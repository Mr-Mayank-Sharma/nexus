package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nexus.core.ai.AgentRunner;
import com.nexus.core.ai.OperationsAssistant;
import com.nexus.core.ai.ScriptedProvider;
import com.nexus.core.ai.ScriptedProvider.Turn;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.SystemPropertyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The operations assistant's evaluation (doc 25, rule 7), run in CI with a scripted model: each case asks a question,
 * the script calls the tools a good answer needs, and the case checks that the tools found the right facts, that no
 * customer detail reached the model, that the answer is grounded, and that the run was audited. With a real key the
 * same questions can be put to the live model; the scripted run guards the tools and the plumbing on every build.
 */
@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsAssistantEvalTest {

    @Autowired OperationsAssistant assistant;
    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired PartyService parties;
    @Autowired SystemPropertyService properties;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String store, product, customer, order, oldOrder;

    @BeforeAll
    void fixtures() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            store = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Eval Hoboken Store", null, null, null, null, null, null, null, null, 30L));
            product = products.create(new ProductService.NewProduct(null, "Eval Boot", null, null, null, null, null, false, false));
            products.setIdentification(product, "SKU", "EVAL-SKU-1");
            inventory.receive(product, store, new BigDecimal("6"), null, null, null, null);
            customer = parties.createPerson(PartyService.PersonName.parse("Zoe Quinn"), null);
            parties.addEmail(customer, "zoe.quinn@example.com", "PRIMARY_EMAIL");
        });
        order = approvedOrderAt(store);
        oldOrder = approvedOrderAt(store);
        jdbc.update("UPDATE order_header SET order_date = ? WHERE order_id = ?", java.sql.Timestamp.valueOf(com.nexus.core.service.CoreClock.now().minusDays(3)), oldOrder);
        String rejected = approvedOrderAt(store);
        tx.executeWithoutResult(s -> reservations.shortPick(rejected, "00001", store, BigDecimal.ONE, "not on the shelf", "t"));
    }

    private String approvedOrderAt(String facility) {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, "WEB_SALES_CHANNEL", "USD", null, null, null, customer, null, null,
                List.of(new OrderService.NewShipGroup("STANDARD", facility, null, null, null, true, false, null, null, null, null)),
                List.of(new OrderService.NewItem(product, "Eval Boot", BigDecimal.ONE, new BigDecimal("80"), null, null, 0)), null, null, null, null,
                "ORDER_APPROVED", null, "t")));
        tx.executeWithoutResult(s -> reservations.reserve(o, "00001", facility, null, "t"));
        return o;
    }

    record Case(String question, List<Turn> script, List<String> toolsExpected, List<String> factsExpected) {
    }

    /** Runs a case and returns what the model saw from the tools. */
    private String run(Case c) {
        ScriptedProvider model = new ScriptedProvider(c.script());
        AgentRunner.Result r = assistant.ask(c.question(), "eval", model);
        assertEquals(c.toolsExpected(), r.toolCalls().stream().map(AgentRunner.ToolCall::name).toList(), c.question());
        assertTrue(r.toolCalls().stream().noneMatch(AgentRunner.ToolCall::error), () -> "tool error: " + r.toolCalls());
        assertTrue(r.grounded(), c.question());
        String seen = model.requests().stream().map(q -> q.messages().toString()).reduce("", String::concat);
        for (String f : c.factsExpected()) assertTrue(seen.contains(f), () -> c.question() + ": the model should have seen " + f + " in " + seen);
        assertFalse(seen.contains("Zoe") || seen.contains("zoe.quinn@example.com"), "customer details never reach the model");
        assertEquals("DONE", jdbc.queryForObject("SELECT status_id FROM ai_agent_run WHERE ai_run_id = ?", String.class, r.runId()));
        return seen;
    }

    private static Function<ArrayNode, String> echo(String prefix) {
        return m -> prefix + ScriptedProvider.toolResults(m).length();
    }

    @Test
    void whereIsAnOrderAndWhoIsItFor() {
        ScriptedProvider model = new ScriptedProvider(List.of(Turn.tools("find_order", "{\"id\":\"" + order + "\"}"),
                Turn.answer(m -> "Order " + order + " for [NAME_1] is approved and ships from Eval Hoboken Store.")));
        AgentRunner.Result r = assistant.ask("Where does order " + order + " ship from, and who is it for?", "eval", model);
        assertEquals("Order " + order + " for Zoe Quinn is approved and ships from Eval Hoboken Store.", r.answer(), "names come back for the person who asked");
        String seen = model.requests().get(1).messages().toString();
        assertTrue(seen.contains("ORDER_APPROVED") && seen.contains("Eval Hoboken Store") && seen.contains("[NAME_1]"));
        assertFalse(seen.contains("Zoe"));
        assertEquals("Y", jdbc.queryForObject("SELECT grounded FROM ai_agent_run WHERE ai_run_id = ?", String.class, r.runId()));
        assertFalse(jdbc.queryForObject("SELECT tool_calls FROM ai_agent_run WHERE ai_run_id = ?", String.class, r.runId()).contains("Zoe"), "the audit keeps the masked form");
    }

    @Test
    void evaluationCases() {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("Which orders are stuck for more than a day?", List.of(Turn.tools("stuck_orders", "{\"olderThanHours\":24,\"limit\":100}"), Turn.answer(echo("stuck: "))),
                List.of("stuck_orders"), List.of(oldOrder, "Eval Hoboken Store")));
        cases.add(new Case("Who can sell EVAL-SKU-1 right now?", List.of(Turn.tools("stock_lookup", "{\"sku\":\"EVAL-SKU-1\"}"), Turn.answer(echo("stock: "))),
                List.of("stock_lookup"), List.of("Eval Hoboken Store", "confidence")));
        cases.add(new Case("Which stores fail to find stock?", List.of(Turn.tools("location_problems", "{\"days\":30}"), Turn.answer(echo("problems: "))),
                List.of("location_problems"), List.of("Eval Hoboken Store", "itemsNotFoundOrMovedOff")));
        cases.add(new Case("How many orders by status this week, and which locations are there?",
                List.of(Turn.tools("order_counts", "{\"sinceDays\":7}", "list_locations", "{}"), Turn.answer(echo("counts: "))),
                List.of("order_counts", "list_locations"), List.of("ORDER_APPROVED", "Eval Hoboken Store", "30")));
        int passed = 0;
        for (Case c : cases) {
            run(c);
            passed++;
        }
        assertEquals(cases.size(), passed);
    }

    @Test
    void anAnswerWithoutToolsIsMarkedNotVerifiedAndTheBudgetHolds() {
        AgentRunner.Result r = assistant.ask("Cancel order " + order, "eval", new ScriptedProvider(List.of(Turn.answer(m -> "I can only read; cancel it on the order screen."))));
        assertFalse(r.grounded());
        assertTrue(r.answer().startsWith("Not verified: "));
        properties.set("nexus.core", "ai.dailyTokens", "10", null);
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> assistant.ask("Anything", "eval", new ScriptedProvider(List.of(Turn.answer(m -> "x")))));
            assertTrue(e.getMessage().contains("daily budget"), e.getMessage());
        } finally {
            properties.set("nexus.core", "ai.dailyTokens", "2000000", null);
        }
    }
}
