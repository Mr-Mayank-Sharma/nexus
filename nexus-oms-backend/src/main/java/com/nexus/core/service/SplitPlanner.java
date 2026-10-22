package com.nexus.core.service;

import com.nexus.core.model.order.OrderItem;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plans a split of a ship group over as few locations as it can. Pure: reads nothing and changes nothing. */
final class SplitPlanner {

    private SplitPlanner() {
    }

    /**
     * Greedy plan: take the location that can fill the most remaining items (ties go to the earlier one in the rule's
     * sort order), then the next, until every item is covered or no location fills any of the rest.
     *
     * @param quantityByItem what each item still needs
     * @param candidates     the locations, already sorted by the rule
     * @return items per facility id, in the order the locations were chosen; items no location can fill are left out
     */
    static Map<String, List<OrderItem>> plan(Map<OrderItem, BigDecimal> quantityByItem, List<CandidateLocation> candidates) {
        Map<String, List<OrderItem>> itemsByFacility = new LinkedHashMap<>();
        Set<OrderItem> notCovered = new HashSet<>(quantityByItem.keySet());

        while (!notCovered.isEmpty()) {
            CandidateLocation bestLocation = null;
            List<OrderItem> bestItems = List.of();
            for (CandidateLocation location : candidates) {
                if (itemsByFacility.containsKey(location.facilityId)) {
                    continue;   // already used in this plan
                }
                List<OrderItem> itemsItCanFill = notCovered.stream()
                        .filter(orderItem -> location.canFill(orderItem, quantityByItem.get(orderItem)))
                        .sorted(Comparator.comparing(OrderItem::getOrderItemSeqId))
                        .toList();
                if (itemsItCanFill.size() > bestItems.size()) {
                    bestLocation = location;
                    bestItems = itemsItCanFill;
                }
            }
            if (bestLocation == null) {
                break;   // no location can fill any of what is left
            }
            itemsByFacility.put(bestLocation.facilityId, bestItems);
            bestItems.forEach(notCovered::remove);
        }
        return itemsByFacility;
    }
}
