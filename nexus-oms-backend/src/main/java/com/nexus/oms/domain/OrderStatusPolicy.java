package com.nexus.oms.domain;

import com.nexus.oms.entity.NxOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * The order lifecycle as a transition table. Order status stays a String column (existing data,
 * channel imports), but every writer goes through here:
 *
 * <ul>
 *   <li>{@link #requireTransition} for user-driven actions (cancel, ship, PUT /orders/{id}/status):
 *       an illegal move throws {@link IllegalOrderTransitionException} (HTTP 409);</li>
 *   <li>{@link #transitionIfAllowed} for automatic flows (routing, waves, packing, reconciliation):
 *       an illegal move is skipped and logged, so a background job never regresses an order
 *       (e.g. PICKING back to ALLOCATED) and never crashes on one odd record.</li>
 * </ul>
 */
public final class OrderStatusPolicy {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusPolicy.class);

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String ALLOCATED = "ALLOCATED";
    public static final String PARTIALLY_ALLOCATED = "PARTIALLY_ALLOCATED";
    public static final String FAILED = "FAILED";
    public static final String PICKING = "PICKING";
    public static final String PACKING = "PACKING";
    public static final String SHIPPED = "SHIPPED";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String DELIVERED = "DELIVERED";
    public static final String FULFILLED = "FULFILLED";
    public static final String RETURNED = "RETURNED";
    public static final String EXCEPTION = "EXCEPTION";
    public static final String CANCELLED = "CANCELLED";
    public static final String MERGED = "MERGED";
    public static final String SPLIT = "SPLIT";

    /** Terminal states: nothing leaves them. */
    /** Every status an order can be in; each one can be published as the topic "order.&lt;status&gt;". */
    public static Set<String> allStatuses() {
        return Set.of(PENDING, CONFIRMED, ALLOCATED, PARTIALLY_ALLOCATED, FAILED, PICKING, PACKING, SHIPPED,
                IN_TRANSIT, DELIVERED, FULFILLED, RETURNED, EXCEPTION, CANCELLED, MERGED, SPLIT);
    }

    public static final Set<String> TERMINAL = Set.of(CANCELLED, MERGED, SPLIT, RETURNED);

    // SHIPPED/FULFILLED straight from intake covers manual "mark as shipped" and orders a
    // merchant fulfilled outside Nexus (channel fulfilment reconciliation).
    private static final Set<String> PRE_ROUTING_EXITS = Set.of(
            CONFIRMED, ALLOCATED, PARTIALLY_ALLOCATED, FAILED, SHIPPED, FULFILLED, CANCELLED, MERGED, SPLIT, EXCEPTION);

    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            entry(PENDING, PRE_ROUTING_EXITS),
            entry(CONFIRMED, Set.of(ALLOCATED, PARTIALLY_ALLOCATED, FAILED, SHIPPED, FULFILLED, CANCELLED, MERGED, SPLIT, EXCEPTION)),
            // A backordered order is retried by brokering, can be cancelled, or re-confirmed.
            entry(FAILED, Set.of(PENDING, CONFIRMED, ALLOCATED, PARTIALLY_ALLOCATED, CANCELLED, EXCEPTION)),
            entry(PARTIALLY_ALLOCATED, Set.of(ALLOCATED, FAILED, PICKING, PACKING, SHIPPED, FULFILLED, CANCELLED, EXCEPTION)),
            entry(ALLOCATED, Set.of(PARTIALLY_ALLOCATED, PICKING, PACKING, SHIPPED, FULFILLED, CANCELLED, EXCEPTION)),
            entry(PICKING, Set.of(PACKING, SHIPPED, FULFILLED, CANCELLED, EXCEPTION)),
            entry(PACKING, Set.of(SHIPPED, FULFILLED, CANCELLED, EXCEPTION)),
            entry(SHIPPED, Set.of(IN_TRANSIT, DELIVERED, FULFILLED, RETURNED, EXCEPTION)),
            entry(IN_TRANSIT, Set.of(DELIVERED, FULFILLED, RETURNED, EXCEPTION)),
            entry(FULFILLED, Set.of(SHIPPED, IN_TRANSIT, DELIVERED, RETURNED, EXCEPTION)),
            entry(DELIVERED, Set.of(FULFILLED, RETURNED, EXCEPTION)),
            // An exception is resolved by moving the order back onto the happy path or cancelling it.
            entry(EXCEPTION, Set.of(PENDING, CONFIRMED, ALLOCATED, PARTIALLY_ALLOCATED, PICKING, PACKING,
                    SHIPPED, IN_TRANSIT, DELIVERED, FULFILLED, CANCELLED, RETURNED)),
            entry(CANCELLED, Set.of()),
            entry(MERGED, Set.of()),
            entry(SPLIT, Set.of()),
            entry(RETURNED, Set.of()));

    private OrderStatusPolicy() {}

    public static String normalise(String status) {
        return status == null || status.isBlank() ? PENDING : status.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isKnown(String status) {
        return ALLOWED.containsKey(normalise(status));
    }

    /** Same-state moves are allowed (idempotent retries); unknown legacy states may only move to known ones. */
    public static boolean isAllowed(String from, String to) {
        String f = normalise(from);
        String t = normalise(to);
        if (!ALLOWED.containsKey(t)) return false;
        if (f.equals(t)) return true;
        Set<String> exits = ALLOWED.get(f);
        if (exits == null) {
            // A value outside the table (old import, channel-specific status): allow recovery
            // into the lifecycle but never out of it to a terminal state silently.
            return !TERMINAL.contains(t) || CANCELLED.equals(t);
        }
        return exits.contains(t);
    }

    public static void requireTransition(NxOrder order, String to) {
        if (!isAllowed(order.getStatus(), to)) {
            throw new IllegalOrderTransitionException(normalise(order.getStatus()), normalise(to));
        }
        order.setStatus(normalise(to));
    }

    /** Applies the move if legal; otherwise leaves the order untouched and returns false. */
    public static boolean transitionIfAllowed(NxOrder order, String to, String actor) {
        if (isAllowed(order.getStatus(), to)) {
            order.setStatus(normalise(to));
            return true;
        }
        log.warn("{} skipped illegal order transition {} -> {} for order {}", actor,
                normalise(order.getStatus()), normalise(to), order.getId());
        return false;
    }

    public static class IllegalOrderTransitionException extends RuntimeException {
        private final String from;
        private final String to;

        public IllegalOrderTransitionException(String from, String to) {
            super("Order is " + from + " and cannot become " + to + ".");
            this.from = from;
            this.to = to;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
    }
}
