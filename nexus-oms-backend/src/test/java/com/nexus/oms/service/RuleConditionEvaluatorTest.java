package com.nexus.oms.service;

import com.nexus.oms.entity.Address;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxRoutingRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RuleConditionEvaluatorTest {

    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator();

    @Test
    void evaluate_nullConditions() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions(null);
        assertTrue(evaluator.evaluate(rule, new NxOrder()));
    }

    @Test
    void evaluate_blankConditions() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("  ");
        assertTrue(evaluator.evaluate(rule, new NxOrder()));
    }

    @Test
    void evaluate_invalidJsonConditions() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("not-json");
        assertTrue(evaluator.evaluate(rule, new NxOrder()));
    }

    @Test
    void evaluate_channelMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"channels\":[\"ONLINE\"]}");
        NxOrder order = new NxOrder();
        order.setChannel("ONLINE");
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_channelNoMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"channels\":[\"ONLINE\"]}");
        NxOrder order = new NxOrder();
        order.setChannel("RETAIL");
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_channelNullOrderChannel() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"channels\":[\"ONLINE\"]}");
        NxOrder order = new NxOrder();
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_channelCaseInsensitive() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"channels\":[\"online\"]}");
        NxOrder order = new NxOrder();
        order.setChannel("Online");
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_fulfillmentTypeMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"fulfillmentTypes\":[\"SHIP\"]}");
        NxOrder order = new NxOrder();
        order.setFulfillmentType("SHIP");
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_fulfillmentTypeNoMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"fulfillmentTypes\":[\"SHIP\"]}");
        NxOrder order = new NxOrder();
        order.setFulfillmentType("PICKUP");
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_maxTotalUnderLimit() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"maxTotal\":\"1000.00\"}");
        NxOrder order = new NxOrder();
        order.setTotal(new BigDecimal("500.00"));
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_maxTotalExceeded() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"maxTotal\":\"1000.00\"}");
        NxOrder order = new NxOrder();
        order.setTotal(new BigDecimal("1500.00"));
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_maxTotalNullOrderTotal() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"maxTotal\":\"1000.00\"}");
        NxOrder order = new NxOrder();
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_minTotalAboveLimit() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"minTotal\":\"100.00\"}");
        NxOrder order = new NxOrder();
        order.setTotal(new BigDecimal("500.00"));
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_minTotalBelowLimit() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"minTotal\":\"100.00\"}");
        NxOrder order = new NxOrder();
        order.setTotal(new BigDecimal("50.00"));
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_regionMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"regions\":[\"CA\"]}");
        NxOrder order = new NxOrder();
        Address address = new Address();
        address.setState("CA");
        order.setShipToAddress(address);
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_regionNoMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"regions\":[\"CA\"]}");
        NxOrder order = new NxOrder();
        Address address = new Address();
        address.setState("NY");
        order.setShipToAddress(address);
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_regionNullAddress() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"regions\":[\"CA\"]}");
        NxOrder order = new NxOrder();
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_priorityMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"priority\":[\"HIGH\"]}");
        NxOrder order = new NxOrder();
        order.setMetadata("{\"priority\":\"HIGH\"}");
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_priorityNoMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"priority\":[\"HIGH\"]}");
        NxOrder order = new NxOrder();
        order.setMetadata("{\"priority\":\"LOW\"}");
        assertFalse(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_priorityNullMetadata() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"priority\":[\"HIGH\"]}");
        NxOrder order = new NxOrder();
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void evaluate_allConditionsMatch() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setConditions("{\"channels\":[\"ONLINE\"],\"fulfillmentTypes\":[\"SHIP\"],\"maxTotal\":\"2000\",\"minTotal\":\"50\",\"regions\":[\"CA\"],\"priority\":[\"HIGH\"]}");
        NxOrder order = new NxOrder();
        order.setChannel("ONLINE");
        order.setFulfillmentType("SHIP");
        order.setTotal(new BigDecimal("500"));
        Address address = new Address();
        address.setState("CA");
        order.setShipToAddress(address);
        order.setMetadata("{\"priority\":\"HIGH\"}");
        assertTrue(evaluator.evaluate(rule, order));
    }

    @Test
    void extractRegion_fromState() {
        Address address = new Address();
        address.setState("CA");
        address.setCountry("US");
        assertEquals("CA", evaluator.extractRegion(address));
    }

    @Test
    void extractRegion_fromCountry() {
        Address address = new Address();
        address.setCountry("US");
        assertEquals("US", evaluator.extractRegion(address));
    }

    @Test
    void extractRegion_nullAddress() {
        assertNull(evaluator.extractRegion(null));
    }

    @Test
    void extractPriority_fromMetadata() {
        assertEquals("HIGH", evaluator.extractPriority("{\"priority\":\"HIGH\"}"));
    }

    @Test
    void extractPriority_noPriorityField() {
        assertNull(evaluator.extractPriority("{\"other\":\"value\"}"));
    }

    @Test
    void extractPriority_nullJson() {
        assertNull(evaluator.extractPriority(null));
    }

    @Test
    void extractPriority_invalidJson() {
        assertNull(evaluator.extractPriority("not-json"));
    }

    @Test
    void parseJson_valid() {
        assertNotNull(evaluator.parseJson("{\"key\":\"value\"}"));
    }

    @Test
    void parseJson_null() {
        assertNull(evaluator.parseJson(null));
    }

    @Test
    void parseJson_blank() {
        assertNull(evaluator.parseJson(""));
    }

    @Test
    void parseJson_invalid() {
        assertNull(evaluator.parseJson("{bad}"));
    }
}
