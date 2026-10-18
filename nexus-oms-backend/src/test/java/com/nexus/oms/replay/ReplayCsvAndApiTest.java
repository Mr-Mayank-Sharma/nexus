package com.nexus.oms.replay;

import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.replay.ReplayEngine.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReplayCsvAndApiTest {

    @Test
    void ordersAreGroupedByOrderId_andColumnNamesAreForgiving() {
        var orders = ReplayCsv.orders("Order Number,Product,Quantity,Latitude,Longitude\nA1,X,2,40.1,-70.2\nA1,Y,1,,\nA2,X,1,,\n");

        assertEquals(2, orders.size());
        assertEquals(2, orders.get(0).lines().size());
        assertEquals(40.1, orders.get(0).lat());
        assertNull(orders.get(1).lat());
    }

    @Test
    void quotedCommasQuotesAndNewlinesInsideCellsAreRead() {
        var rows = ReplayCsv.rows("sku,note\n\"A,B\",\"he said \"\"hi\"\"\nthen left\"\n", "x");
        assertEquals("A,B", rows.get(0).get("sku"));
        assertEquals("he said \"hi\"\nthen left", rows.get(0).get("note"));
    }

    @Test
    void aBadFileIsRefusedWithTheLineNumberOfEachProblem() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> ReplayCsv.orders("order_id,sku,qty\nA1,X,two\nA2,,3\nA3,X,0\n"));
        assertTrue(e.getMessage().startsWith("3 problem(s)"), e.getMessage());
        assertTrue(e.getMessage().contains("orders line 2: 'two' is not a number"), e.getMessage());
        assertTrue(e.getMessage().contains("orders line 3: needs an order_id and a sku"), e.getMessage());
        assertTrue(e.getMessage().contains("orders line 4: qty must be at least 1"), e.getMessage());
    }

    @Test
    void stockNegativesAreTreatedAsNone_andRowsForTheSamePlaceAreSummed() {
        var stock = ReplayCsv.stock("sku,node_id,qty\nX,N1,-4\nX,N1,3\nX,N2,5\n");
        assertEquals(3, stock.get("X").get("N1"));   // -4 becomes 0, then +3
        assertEquals(5, stock.get("X").get("N2"));
    }

    @Test
    void aFailureRateGivenAsAPercentageIsNormalised_andOutOfRangeIsRefused() {
        var nodes = ReplayCsv.nodes("node_id,failure_rate\nA,12\nB,0.05\n");
        assertEquals(0.12, nodes.get(0).failureRate(), 1e-9);
        assertEquals(0.05, nodes.get(1).failureRate(), 1e-9);
        assertThrows(BadRequestException.class, () -> ReplayCsv.nodes("node_id,failure_rate\nA,250\n"));
        assertThrows(BadRequestException.class, () -> ReplayCsv.nodes("node_id\nA\nA\n"));
    }

    @Test
    void emptyAndHeaderOnlyFilesAreExplained() {
        assertThrows(BadRequestException.class, () -> ReplayCsv.orders(""));
        BadRequestException e = assertThrows(BadRequestException.class, () -> ReplayCsv.orders("order_id,sku,qty\n"));
        assertTrue(e.getMessage().contains("order_id, sku, qty"));
    }

    @Test
    void theBuiltInSampleRunsAndShowsTheDifferencesThePitchPromises() {
        var files = ReplaySample.build();
        @SuppressWarnings("unchecked")
        var out = ReplayController.run(files.orders(), files.stock(), files.nodes(), Costs.defaults());
        Result r = (Result) out.get("result");

        Metrics nearest = r.strategies().get(0), fewest = r.strategies().get(1), aware = r.strategies().get(2);
        assertEquals(220, out.get("orders"));
        assertTrue(nearest.splitOrders() > fewest.splitOrders(), "keeping orders together must beat splitting by line on this data");
        assertTrue(aware.expectedFailedUnits() < fewest.expectedFailedUnits(), "avoiding the 22% store must reduce expected failed picks");
        assertTrue(aware.totalCost() < nearest.totalCost());
        assertTrue(r.usedDistance());
        assertTrue(r.usedFailureRates());
        assertTrue(r.differencesTotal() > 0);
        assertTrue(r.differences().size() <= ReplayEngine.MAX_DIFFERENCES);
    }

    @Test
    void locationsThatOnlyAppearInStockStillTakePart() {
        var out = ReplayController.run("order_id,sku,qty\nA,X,1\n", "sku,node_id,qty\nX,ONLY-IN-STOCK,1\n", null, Costs.defaults());
        Result r = (Result) out.get("result");
        assertEquals(1, out.get("locations"));
        assertEquals(1, r.strategies().get(0).filled());
    }
}
