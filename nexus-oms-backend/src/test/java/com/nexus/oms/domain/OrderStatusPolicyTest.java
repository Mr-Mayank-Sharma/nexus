package com.nexus.oms.domain;

import com.nexus.oms.entity.NxOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.nexus.oms.domain.OrderStatusPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @CsvSource({
            "PENDING, CONFIRMED", "PENDING, ALLOCATED", "CONFIRMED, PARTIALLY_ALLOCATED", "FAILED, ALLOCATED",
            "ALLOCATED, PICKING", "PICKING, PACKING", "PACKING, SHIPPED", "SHIPPED, DELIVERED",
            "DELIVERED, RETURNED", "PENDING, SHIPPED", "EXCEPTION, ALLOCATED", "ALLOCATED, ALLOCATED",
            "pending, cancelled", ", CONFIRMED"
    })
    void legalMoves(String from, String to) {
        assertTrue(isAllowed(from, to));
    }

    @ParameterizedTest(name = "{0} -> {1} rejected")
    @CsvSource({
            "SHIPPED, CANCELLED", "DELIVERED, CANCELLED", "DELIVERED, PENDING", "CANCELLED, ALLOCATED",
            "PICKING, ALLOCATED", "SHIPPED, ALLOCATED", "MERGED, PENDING", "RETURNED, SHIPPED",
            "PENDING, PROCESSING", "ALLOCATED, NONSENSE"
    })
    void illegalMoves(String from, String to) {
        assertFalse(isAllowed(from, to));
    }

    @Test
    void everyStateHasAnEntry_andTerminalStatesHaveNoExits() {
        for (String terminal : TERMINAL) {
            assertTrue(ALLOWED.get(terminal).isEmpty(), terminal);
        }
        ALLOWED.values().forEach(exits -> exits.forEach(t -> assertTrue(ALLOWED.containsKey(t), "unknown target " + t)));
    }

    @Test
    void requireTransition_throwsAndLeavesOrderUnchanged() {
        NxOrder order = new NxOrder();
        order.setStatus("DELIVERED");

        IllegalOrderTransitionException e = assertThrows(IllegalOrderTransitionException.class,
                () -> requireTransition(order, CANCELLED));
        assertEquals("Order is DELIVERED and cannot become CANCELLED.", e.getMessage());
        assertEquals("DELIVERED", order.getStatus());
    }

    @Test
    void transitionIfAllowed_skipsRegressionsQuietly() {
        NxOrder order = new NxOrder();
        order.setStatus("PICKING");

        assertFalse(transitionIfAllowed(order, ALLOCATED, "test"));
        assertEquals("PICKING", order.getStatus());
        assertTrue(transitionIfAllowed(order, PACKING, "test"));
        assertEquals("PACKING", order.getStatus());
    }

    @Test
    void legacyUnknownStatus_canRecoverButNotSilentlyTerminate() {
        assertTrue(isAllowed("AWAITING_PAYMENT", CONFIRMED));
        assertTrue(isAllowed("AWAITING_PAYMENT", CANCELLED));
        assertFalse(isAllowed("AWAITING_PAYMENT", MERGED));
    }
}
