package com.nexus.oms.replay;

import com.nexus.oms.replay.ReplayEngine.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReplayEngineTest {

    private static Map<String, Map<String, Integer>> stock(Object... triples) {
        Map<String, Map<String, Integer>> m = new HashMap<>();
        for (int i = 0; i < triples.length; i += 3) {
            m.computeIfAbsent((String) triples[i], k -> new HashMap<>()).put((String) triples[i + 1], (Integer) triples[i + 2]);
        }
        return m;
    }

    private static Order order(String id, Object... skuQty) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < skuQty.length; i += 2) lines.add(new Line((String) skuQty[i], (Integer) skuQty[i + 1]));
        return new Order(id, lines, null, null);
    }

    private static Node node(String id, Double rate) { return new Node(id, id, null, null, rate); }

    private static Metrics of(Result r, Strategy s) {
        return r.strategies().stream().filter(m -> m.strategy() == s).findFirst().orElseThrow();
    }

    @Test
    void anOrderSplitByNearestFirstStaysTogetherWhenOneLocationHoldsItAll() {
        // N1 holds X, N2 holds Y, N3 holds both. Listed in that order, so "nearest" means N1 then N2 then N3.
        var nodes = List.of(node("N1", null), node("N2", null), node("N3", null));
        var s = stock("X", "N1", 1, "Y", "N2", 1, "X", "N3", 1, "Y", "N3", 1);

        Result r = ReplayEngine.run(nodes, s, List.of(order("O1", "X", 1, "Y", 1)), null);

        assertEquals(2, of(r, Strategy.NEAREST_FIRST).shipments());
        assertEquals(1, of(r, Strategy.NEAREST_FIRST).splitOrders());
        assertEquals(1, of(r, Strategy.FEWEST_SHIPMENTS).shipments());
        assertEquals(0, of(r, Strategy.FEWEST_SHIPMENTS).splitOrders());
        assertEquals(1, r.differencesTotal());
        assertEquals("N1 + N2", r.differences().get(0).before());
        assertEquals("N3", r.differences().get(0).after());
    }

    @Test
    void failureAwareChoosesTheLessFailingLocationAmongEquals_andExpectedFailuresDropAccordingly() {
        // N1 and N2 can both fill the order; N1 fails 30% of picks, N2 2%. Two units are ordered.
        var nodes = List.of(node("N1", 0.30), node("N2", 0.02));
        var s = stock("X", "N1", 5, "X", "N2", 5);

        Result r = ReplayEngine.run(nodes, s, List.of(order("O1", "X", 2)), new Costs(10, 0, 10));

        Metrics fewest = of(r, Strategy.FEWEST_SHIPMENTS), aware = of(r, Strategy.FAILURE_AWARE);
        assertEquals(0.60, fewest.expectedFailedUnits(), 1e-9);   // 2 units x 0.30
        assertEquals(0.04, aware.expectedFailedUnits(), 1e-9);    // 2 units x 0.02
        assertEquals(10.0 + 6.0, fewest.totalCost(), 1e-9);       // 1 shipment + 0.6 x 10 rework
        assertEquals(10.0 + 0.4, aware.totalCost(), 1e-9);
        assertTrue(aware.totalCost() < fewest.totalCost());
    }

    @Test
    void stockIsConsumedOrderByOrder() {
        var nodes = List.of(node("N1", null));
        var s = stock("X", "N1", 3);
        var orders = List.of(order("O1", "X", 2), order("O2", "X", 2), order("O3", "X", 5));

        Metrics m = of(ReplayEngine.run(nodes, s, orders, null), Strategy.FEWEST_SHIPMENTS);

        assertEquals(1, m.filled());       // O1
        assertEquals(1, m.partial());      // O2 gets the last unit
        assertEquals(1, m.unfillable());   // O3 finds none
        assertEquals(1 + 5, m.unfilledUnits());
        assertEquals(2, m.shipments());
    }

    @Test
    void everyRuleSeesTheSameStock_andTheCallersMapIsNotChanged() {
        var nodes = List.of(node("N1", null), node("N2", null));
        var s = stock("X", "N1", 1, "X", "N2", 1);
        var snapshot = new HashMap<String, Map<String, Integer>>();
        s.forEach((k, v) -> snapshot.put(k, new HashMap<>(v)));

        Result r = ReplayEngine.run(nodes, s, List.of(order("O1", "X", 1), order("O2", "X", 1)), null);

        assertEquals(snapshot, s, "the snapshot a caller passes in must stay as it was");
        for (Strategy st : Strategy.values()) assertEquals(2, of(r, st).filled(), st + " should fill both orders from the same starting stock");
    }

    @Test
    void whenNoSingleLocationHasEverything_itCoversWithAsFewAsPossible() {
        // Needs X2 and Y2 and Z2. N1 has X and Y, N2 has Z, N3 has X only.
        var nodes = List.of(node("N3", null), node("N1", null), node("N2", null));
        var s = stock("X", "N1", 2, "Y", "N1", 2, "Z", "N2", 2, "X", "N3", 2);

        Metrics m = of(ReplayEngine.run(nodes, s, List.of(order("O1", "X", 2, "Y", 2, "Z", 2)), null), Strategy.FEWEST_SHIPMENTS);

        assertEquals(2, m.shipments());   // N1 (4 units) + N2 (2 units), not N3 as well
        assertEquals(0, m.unfilledUnits());
    }

    @Test
    void distanceDecidesWhatIsNearest_whenCoordinatesAreGiven() {
        var near = new Node("NEAR", "NEAR", 10.0, 10.0, null);
        var far = new Node("FAR", "FAR", 50.0, 50.0, null);
        var order = new Order("O1", List.of(new Line("X", 1)), 10.1, 10.1);
        var s = stock("X", "NEAR", 1, "X", "FAR", 1);

        // FAR is listed first, so without coordinates it would win; with them NEAR does.
        Result r = ReplayEngine.run(List.of(far, near), s, List.of(order), new Costs(5, 0.01, 5));

        assertTrue(r.usedDistance());
        Metrics m = of(r, Strategy.NEAREST_FIRST);
        assertEquals(1, m.shipments());
        assertTrue(m.shippingCost() < 5.2, "a tenth of a degree away adds a few cents, not a hundred dollars: " + m.shippingCost());
    }

    @Test
    void theAssumptionsSayWhatWasNotAvailable() {
        Result bare = ReplayEngine.run(List.of(node("N1", null)), stock("X", "N1", 1), List.of(order("O1", "X", 1)), null);
        String text = String.join(" ", bare.assumptions());
        assertTrue(text.contains("No coordinates"), text);
        assertTrue(text.contains("No pick-failure rates"), text);
        assertFalse(bare.usedDistance());
        assertFalse(bare.usedFailureRates());
        assertTrue(text.contains("not modelled"));
    }

    @Test
    void anEmptyOrderListDoesNotDivideByZero() {
        Metrics m = of(ReplayEngine.run(List.of(node("N1", null)), stock("X", "N1", 1), List.of(), null), Strategy.NEAREST_FIRST);
        assertEquals(0, m.orders());
        assertEquals(0.0, m.splitRate());
    }
}
