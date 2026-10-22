package com.nexus.core.service;

import com.nexus.core.model.order.OrderItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A location a routing rule may use for one ship group: what it can promise per product, and the numbers the rule's
 * sorts compare. Filled in by {@link LocationFinder}; a field left at its default means "not known / not limited".
 */
final class CandidateLocation {

    static final double DEFAULT_RELIABILITY = 0.9;

    final String facilityId;
    /** What the location can promise, per product id. */
    final Map<String, BigDecimal> sellableByProduct = new HashMap<>();
    /** Null when the location or the ship-to address has no coordinates. */
    Double distanceKm;
    /** The location's place in the rule's facility group; Long.MAX_VALUE when the rule names no group. */
    long groupSequence = Long.MAX_VALUE;
    /** The highest daily sales of the products asked for. */
    double salesVelocity;
    /** The lowest weeks of supply of the products asked for. */
    double weeksOfSupply = Double.MAX_VALUE;
    /** Share of the style's sizes still in stock (1 = full run); only worked out when the rule sorts by it. */
    double sizeRunCompleteness = 1;
    /** Orders the location may still take today; Long.MAX_VALUE when it has no limit. */
    long capacityLeft = Long.MAX_VALUE;
    /** Share of routed orders the location picks (from the store reliability score). */
    double reliability = DEFAULT_RELIABILITY;

    CandidateLocation(String facilityId) {
        this.facilityId = facilityId;
    }

    /** Can this location promise the quantity of the item? */
    boolean canFill(OrderItem orderItem, BigDecimal quantity) {
        return sellableByProduct.getOrDefault(orderItem.getProductId(), BigDecimal.ZERO).compareTo(quantity) >= 0;
    }

    /** Can this location promise every item? */
    boolean canFillAll(Map<OrderItem, BigDecimal> quantityByItem) {
        return quantityByItem.entrySet().stream().allMatch(entry -> canFill(entry.getKey(), entry.getValue()));
    }

    double totalSellable() {
        return sellableByProduct.values().stream().mapToDouble(BigDecimal::doubleValue).sum();
    }

    /** Why the rule chose this location, in plain words, from the numbers that are known. */
    String explanation(List<String> sortNames, boolean holdsEverything) {
        List<String> parts = new ArrayList<>();
        if (holdsEverything) {
            parts.add("holds every item");
        }
        if (distanceKm != null) {
            parts.add(String.format(Locale.ROOT, "%.1f km away", distanceKm));
        }
        if (groupSequence != Long.MAX_VALUE) {
            parts.add("place " + groupSequence + " in its group");
        }
        if (capacityLeft != Long.MAX_VALUE) {
            parts.add(capacityLeft + " orders left today");
        }
        if (sortNames.contains("reliability")) {
            parts.add(String.format(Locale.ROOT, "picks %.0f%% of its orders", reliability * 100));
        }
        if (!sortNames.isEmpty()) {
            parts.add("sorted by " + String.join(", ", sortNames));
        }
        return String.join("; ", parts);
    }
}
