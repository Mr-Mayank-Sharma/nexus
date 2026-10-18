package com.nexus.oms.replay;

import java.util.*;

/**
 * Replays a set of orders, one at a time, against a stock snapshot under different routing rules and reports
 * what each rule would have done: how many orders split, how many shipments, what could not be filled, how many
 * picks are expected to fail and a rough cost. It touches no database and changes nothing.
 *
 * <p>This is a simulation, not a forecast. It uses the stock as it was at the start and consumes it order by
 * order. It does not model replenishment during the period, cancellations, carrier rate tables or changes in
 * demand, and its cost is a simple, adjustable model. Its use is comparing rules on the same orders, which is
 * fair because every rule sees exactly the same stock and the same orders.
 */
public final class ReplayEngine {

    private ReplayEngine() {}

    public enum Strategy {
        /** Per line, the nearest store that has it (what a basic "closest location" rule does). Tends to split. */
        NEAREST_FIRST("Nearest location that has it",
                "Each line comes from the closest location that holds it, with no attempt to keep an order together."),
        /** One location for the whole order if any can; otherwise as few locations as possible. */
        FEWEST_SHIPMENTS("Fewest shipments",
                "Prefers one location that holds the whole order, then covers the rest with as few locations as possible."),
        /** Fewest shipments, but among equal choices the location that fails picks least. */
        FAILURE_AWARE("Fewest shipments, avoiding stores that fail picks",
                "As above, but among locations that could take the order it picks the one least likely to fail the pick.");

        public final String label;
        public final String description;

        Strategy(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public record Node(String id, String name, Double lat, Double lon, Double failureRate) {}

    public record Line(String sku, int qty) {}

    public record Order(String id, List<Line> lines, Double lat, Double lon) {}

    /** @param perShipment cost of one shipment; @param perKm added per kilometre when coordinates are known;
     *  @param perFailedUnit rework cost of each unit whose pick fails (it has to be sent again from somewhere else) */
    public record Costs(double perShipment, double perKm, double perFailedUnit) {
        public static Costs defaults() {
            return new Costs(6.50, 0.0, 6.50);
        }
    }

    public record Metrics(Strategy strategy, String label, int orders, int filled, int partial, int unfillable,
                          int shipments, int splitOrders, double splitRate, double shipmentsPerOrder,
                          int unfilledUnits, double expectedFailedUnits,
                          double shippingCost, double reworkCost, double totalCost) {}

    /** An order whose shipments differ between the first strategy and another one. */
    public record Difference(String orderId, int shipmentsBefore, int shipmentsAfter, String before, String after) {}

    public record Result(List<Metrics> strategies, List<Difference> differences, int differencesTotal,
                         boolean usedDistance, boolean usedFailureRates, List<String> assumptions) {}

    public static final double PRIOR_FAILURE_RATE = 0.05;
    static final int MAX_DIFFERENCES = 200;

    /** @param stock sku to (node id to units) */
    public static Result run(List<Node> nodes, Map<String, Map<String, Integer>> stock, List<Order> orders, Costs costs) {
        Costs c = costs == null ? Costs.defaults() : costs;
        boolean haveDistance = orders.stream().anyMatch(o -> o.lat() != null && o.lon() != null)
                && nodes.stream().anyMatch(n -> n.lat() != null && n.lon() != null);
        boolean haveRates = nodes.stream().anyMatch(n -> n.failureRate() != null);

        Map<Strategy, Plan> plans = new LinkedHashMap<>();
        for (Strategy s : Strategy.values()) plans.put(s, simulate(s, nodes, deepCopy(stock), orders, c, haveDistance));

        List<Metrics> metrics = plans.values().stream().map(p -> p.metrics).toList();
        List<Difference> diffs = new ArrayList<>();
        int total = 0;
        Plan base = plans.get(Strategy.NEAREST_FIRST);
        Plan best = plans.get(Strategy.FAILURE_AWARE);
        for (Order o : orders) {
            List<String> a = base.shipmentsByOrder.get(o.id()), b = best.shipmentsByOrder.get(o.id());
            if (a != null && b != null && a.size() != b.size()) {
                total++;
                if (diffs.size() < MAX_DIFFERENCES) diffs.add(new Difference(o.id(), a.size(), b.size(), String.join(" + ", a), String.join(" + ", b)));
            }
        }
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Orders are processed in the order given, each against the stock the earlier orders left.");
        assumptions.add("Stock is the snapshot you supplied. Replenishment, cancellations and returns during the period are not modelled.");
        assumptions.add("Cost = shipments x " + c.perShipment() + (haveDistance && c.perKm() > 0 ? " + distance x " + c.perKm() + " per km" : "")
                + " + expected failed units x " + c.perFailedUnit() + ". It is a comparison aid, not a carrier quote.");
        if (!haveDistance) assumptions.add("No coordinates were supplied, so 'nearest' follows the order the locations were listed in.");
        if (!haveRates) assumptions.add("No pick-failure rates were supplied, so every location is assumed to fail "
                + (int) (PRIOR_FAILURE_RATE * 100) + "% of picks and the failure-aware rule cannot differ from fewest shipments.");
        return new Result(metrics, diffs, total, haveDistance, haveRates, assumptions);
    }

    // ---- simulation -----------------------------------------------------------------------------------------

    private record Plan(Metrics metrics, Map<String, List<String>> shipmentsByOrder) {}

    private static Plan simulate(Strategy strategy, List<Node> nodes, Map<String, Map<String, Integer>> stock,
                                 List<Order> orders, Costs c, boolean haveDistance) {
        Map<String, Node> byId = new HashMap<>();
        for (Node n : nodes) byId.put(n.id(), n);
        Map<String, List<String>> shipmentsByOrder = new LinkedHashMap<>();
        int filled = 0, partial = 0, unfillable = 0, shipments = 0, splitOrders = 0, unfilledUnits = 0;
        double failed = 0, km = 0;

        for (Order order : orders) {
            List<Node> ranked = rank(strategy, nodes, order, haveDistance);
            Map<String, Map<String, Integer>> taken = new LinkedHashMap<>();   // node -> sku -> units
            Map<String, Integer> remaining = new LinkedHashMap<>();
            for (Line l : order.lines()) remaining.merge(l.sku(), l.qty(), Integer::sum);

            if (strategy == Strategy.NEAREST_FIRST) {
                for (Map.Entry<String, Integer> line : new ArrayList<>(remaining.entrySet())) {
                    int need = line.getValue();
                    for (Node n : ranked) {
                        if (need == 0) break;
                        int have = available(stock, line.getKey(), n.id());
                        int use = Math.min(need, have);
                        if (use > 0) {
                            take(stock, taken, line.getKey(), n.id(), use);
                            need -= use;
                        }
                    }
                    remaining.put(line.getKey(), need);
                }
            } else {
                Node whole = ranked.stream().filter(n -> canFillAll(stock, remaining, n.id())).findFirst().orElse(null);
                if (whole != null) {
                    for (Map.Entry<String, Integer> line : remaining.entrySet()) take(stock, taken, line.getKey(), whole.id(), line.getValue());
                    remaining.replaceAll((k, v) -> 0);
                } else {
                    while (remaining.values().stream().anyMatch(v -> v > 0)) {
                        Node pick = null;
                        int bestCover = 0;
                        for (Node n : ranked) {   // ranked order breaks ties
                            int cover = 0;
                            for (Map.Entry<String, Integer> line : remaining.entrySet()) {
                                cover += Math.min(line.getValue(), available(stock, line.getKey(), n.id()));
                            }
                            if (cover > bestCover) { bestCover = cover; pick = n; }
                        }
                        if (pick == null) break;
                        for (Map.Entry<String, Integer> line : remaining.entrySet()) {
                            int use = Math.min(line.getValue(), available(stock, line.getKey(), pick.id()));
                            if (use > 0) {
                                take(stock, taken, line.getKey(), pick.id(), use);
                                line.setValue(line.getValue() - use);
                            }
                        }
                    }
                }
            }

            int unfilled = remaining.values().stream().mapToInt(Integer::intValue).sum();
            unfilledUnits += unfilled;
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> t : taken.entrySet()) {
                Node n = byId.get(t.getKey());
                names.add(n == null ? t.getKey() : n.name() == null || n.name().isBlank() ? n.id() : n.name());
                int units = t.getValue().values().stream().mapToInt(Integer::intValue).sum();
                failed += units * rateOf(n);
                if (haveDistance && n != null) km += distanceKm(n, order);
            }
            shipmentsByOrder.put(order.id(), names);
            shipments += taken.size();
            if (taken.size() > 1) splitOrders++;
            if (taken.isEmpty()) unfillable++; else if (unfilled > 0) partial++; else filled++;
        }
        int n = orders.size();
        double ship = shipments * c.perShipment() + km * c.perKm();
        double rework = failed * c.perFailedUnit();
        Metrics m = new Metrics(strategy, strategy.label, n, filled, partial, unfillable, shipments, splitOrders,
                n == 0 ? 0 : round4((double) splitOrders / n), n == 0 ? 0 : round4((double) shipments / n),
                unfilledUnits, round2(failed), round2(ship), round2(rework), round2(ship + rework));
        return new Plan(m, shipmentsByOrder);
    }

    private static List<Node> rank(Strategy s, List<Node> nodes, Order order, boolean haveDistance) {
        List<Node> ranked = new ArrayList<>(nodes);
        Comparator<Node> byDistance = haveDistance && order.lat() != null && order.lon() != null
                ? Comparator.comparingDouble(n -> n.lat() == null || n.lon() == null ? Double.MAX_VALUE : distanceKm(n, order))
                : (a, b) -> 0;   // stable: keeps the order the locations were listed in
        Comparator<Node> cmp = s == Strategy.FAILURE_AWARE
                ? Comparator.<Node>comparingDouble(ReplayEngine::rateOf).thenComparing(byDistance)
                : byDistance;
        ranked.sort(cmp);
        return ranked;
    }

    private static double rateOf(Node n) {
        return n == null || n.failureRate() == null ? PRIOR_FAILURE_RATE : n.failureRate();
    }

    private static boolean canFillAll(Map<String, Map<String, Integer>> stock, Map<String, Integer> need, String node) {
        for (Map.Entry<String, Integer> e : need.entrySet()) if (available(stock, e.getKey(), node) < e.getValue()) return false;
        return true;
    }

    private static int available(Map<String, Map<String, Integer>> stock, String sku, String node) {
        Map<String, Integer> bySku = stock.get(sku);
        return bySku == null ? 0 : bySku.getOrDefault(node, 0);
    }

    private static void take(Map<String, Map<String, Integer>> stock, Map<String, Map<String, Integer>> taken, String sku, String node, int units) {
        stock.get(sku).merge(node, -units, Integer::sum);
        taken.computeIfAbsent(node, k -> new LinkedHashMap<>()).merge(sku, units, Integer::sum);
    }

    private static Map<String, Map<String, Integer>> deepCopy(Map<String, Map<String, Integer>> src) {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        src.forEach((k, v) -> out.put(k, new HashMap<>(v)));
        return out;
    }

    static double distanceKm(Node n, Order o) {
        if (n.lat() == null || n.lon() == null || o.lat() == null || o.lon() == null) return 0;
        double r = 6371.0;
        double dLat = Math.toRadians(o.lat() - n.lat()), dLon = Math.toRadians(o.lon() - n.lon());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(n.lat())) * Math.cos(Math.toRadians(o.lat())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
