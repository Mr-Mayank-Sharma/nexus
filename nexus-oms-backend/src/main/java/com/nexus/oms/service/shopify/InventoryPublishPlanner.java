package com.nexus.oms.service.shopify;

import com.nexus.oms.entity.NxInventory;

import java.time.LocalDate;
import java.util.*;

/**
 * Decides how many units of a SKU a sales channel may offer at one channel location.
 *
 * <p>The rules, all explicit and per node so they can be audited line by line:
 * <ol>
 *   <li>Only the nodes mapped to that channel location contribute. Stock of an unmapped node is
 *       never published anywhere, and a node maps to one location per store, so no unit is offered
 *       twice.</li>
 *   <li>A node contributes <b>on hand − allocated − reserved − damaged − safety stock − the
 *       mapping's buffer</b>, using only lots that have not expired.</li>
 *   <li>That figure is floored at zero <b>per node, before summing</b>: one oversold node must not
 *       cancel out units that another node genuinely has.</li>
 *   <li>A mapping may cap a node's contribution; inactive nodes and disabled mappings contribute
 *       nothing.</li>
 * </ol>
 * The location's quantity is the sum of its nodes' contributions.
 */
public final class InventoryPublishPlanner {

    private InventoryPublishPlanner() {
    }

    /** A node as the planner sees it. */
    public record Node(UUID id, String name, boolean active) {
    }

    /** How one node's stock was treated for one SKU; serialised into the reconciliation report. */
    public record Contribution(UUID nodeId, String nodeName, int onHand, int allocated, int reserved,
                               int damaged, int safetyStock, int buffer, int expiredLotUnits,
                               int sellable, Integer cappedTo, int contributed, String excluded) {
    }

    public record LocationQuantity(String externalLocationId, String sku, int quantity, List<Contribution> contributions) {
    }

    /** The mapping fields the planner needs. */
    public record Rule(UUID nodeId, String externalLocationId, boolean enabled, int bufferUnits, Integer contributionCap) {
    }

    /**
     * @param rows every inventory row of the SKU for the tenant (all nodes, all lots)
     * @return one result per channel location that has at least one enabled mapping
     */
    public static List<LocationQuantity> plan(String sku, List<NxInventory> rows, Collection<Rule> rules,
                                              Map<UUID, Node> nodes, LocalDate today) {
        Map<String, List<Contribution>> byLocation = new TreeMap<>();
        for (Rule rule : rules) {
            if (!rule.enabled()) continue;
            Node node = nodes.get(rule.nodeId());
            List<NxInventory> nodeRows = rows.stream().filter(r -> rule.nodeId().equals(r.getNodeId())).toList();
            byLocation.computeIfAbsent(rule.externalLocationId(), k -> new ArrayList<>())
                    .add(contribution(node, rule, nodeRows, today));
        }
        List<LocationQuantity> result = new ArrayList<>();
        byLocation.forEach((location, contributions) -> {
            contributions.sort(Comparator.comparing(c -> String.valueOf(c.nodeName())));
            int total = contributions.stream().mapToInt(Contribution::contributed).sum();
            result.add(new LocationQuantity(location, sku, total, List.copyOf(contributions)));
        });
        return result;
    }

    static Contribution contribution(Node node, Rule rule, List<NxInventory> rows, LocalDate today) {
        String name = node != null ? node.name() : null;
        if (node == null) {
            return new Contribution(rule.nodeId(), null, 0, 0, 0, 0, 0, rule.bufferUnits(), 0, 0, null, 0, "NODE_NOT_FOUND");
        }
        int onHand = 0, allocated = 0, reserved = 0, damaged = 0, safety = 0, expired = 0;
        for (NxInventory row : rows) {
            if (row.getExpiryDate() != null && row.getExpiryDate().isBefore(today)) {
                expired += nz(row.getQuantityOnHand());
                continue;   // an expired lot is not sellable and its reservations are moot
            }
            onHand += nz(row.getQuantityOnHand());
            allocated += nz(row.getQuantityAllocated());
            reserved += nz(row.getQuantityReserved());
            damaged += nz(row.getQuantityDamaged());
            safety += nz(row.getSafetyStock());
        }
        int sellable = Math.max(0, onHand - allocated - reserved - damaged - safety - rule.bufferUnits());
        Integer cap = rule.contributionCap();
        int contributed = cap != null ? Math.min(sellable, cap) : sellable;
        String excluded = !node.active() ? "NODE_INACTIVE" : null;
        if (excluded != null) contributed = 0;
        return new Contribution(node.id(), name, onHand, allocated, reserved, damaged, safety, rule.bufferUnits(),
                expired, sellable, cap, contributed, excluded);
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }
}
