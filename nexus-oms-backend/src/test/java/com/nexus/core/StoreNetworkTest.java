package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import com.nexus.core.service.StatusService;
import com.nexus.core.service.StoreNetworkService;
import com.nexus.core.service.TransferService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreNetworkTest {

    @Autowired TransferService transfers;
    @Autowired StoreNetworkService network;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired ProductStoreService stores;
    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired PartyService parties;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    static final AtomicInteger N = new AtomicInteger();
    String customer;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        customer = tx.execute(s -> parties.createPerson(PartyService.PersonName.parse("Net Shopper"), null));
    }

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    private String product(String name) {
        return tx.execute(s -> products.create(new ProductService.NewProduct(null, name + "-" + N.incrementAndGet(), null, null, null, null, null, false, false)));
    }

    private String store(String name) {
        return tx.execute(s -> facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", name + "-" + N.incrementAndGet(), null, null, null, null, null, null, null, null, null)));
    }

    private void stock(String p, String f, String qty) {
        tx.executeWithoutResult(s -> inventory.receive(p, f, q(qty), null, null, null, null));
    }

    private void same(String expected, BigDecimal actual) {
        assertEquals(0, q(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private String xferStatus(String shipmentId, int line) {
        return transfers.lines(shipmentId).get(line).getStatusId();
    }

    @Test
    void aTransferHoldsShipsInTransitAndReceivesWithStockRightAtEveryStep() {
        String bag = product("bag"), belt = product("belt");
        String a = store("A"), b = store("B");
        stock(bag, a, "10");
        stock(belt, a, "4");
        String t = tx.execute(s -> transfers.request(a, b, List.of(new TransferService.Line(bag, q("6")), new TransferService.Line(belt, q("2"))), "rebalance", "t"));
        same("10", inventory.atp(bag, a));                                  // requested: nothing moves

        tx.executeWithoutResult(s -> transfers.approve(t, "t"));
        same("4", inventory.atp(bag, a));                                   // approved: can't be sold at A any more
        same("10", inventory.onHand(bag, a));

        BigDecimal transitBefore = inventory.onHand(bag, TransferService.IN_TRANSIT);
        tx.executeWithoutResult(s -> transfers.ship(t, "TRK-1", "t"));
        same("4", inventory.onHand(bag, a));                                // shipped: off A's shelf
        same("4", inventory.atp(bag, a));
        assertEquals(0, transitBefore.add(q("6")).compareTo(inventory.onHand(bag, TransferService.IN_TRANSIT)), "owned by IN_TRANSIT on the way");
        same("0", inventory.atp(bag, TransferService.IN_TRANSIT));          // and never for sale
        assertEquals("IXF_EN_ROUTE", xferStatus(t, 0));

        List<TransferService.Receipt> r = tx.execute(s -> transfers.receive(t, Map.of(bag, q("6"), belt, q("2")), "t"));
        assertTrue(r.stream().allMatch(x -> "RECEIVED".equals(x.outcome())));
        same("6", inventory.atp(bag, b));
        assertEquals(0, transitBefore.compareTo(inventory.onHand(bag, TransferService.IN_TRANSIT)));
        assertEquals("SHIPMENT_DELIVERED", jdbc.queryForObject("SELECT status_id FROM shipment WHERE shipment_id = ?", String.class, t));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM shipment_receipt WHERE shipment_id = ?", Long.class, t));
    }

    @Test
    void aShortReceiptWithinToleranceSettlesItselfAndABigOneWaitsForAPerson() {
        String socks = product("socks"), hats = product("hats");
        String a = store("A"), b = store("B");
        stock(socks, a, "200");
        stock(hats, a, "10");
        String t = tx.execute(s -> transfers.request(a, b, List.of(new TransferService.Line(socks, q("100")), new TransferService.Line(hats, q("10"))), null, "t"));
        tx.executeWithoutResult(s -> {
            transfers.approve(t, "t");
            transfers.ship(t, null, "t");
        });
        // 2% of 100 is 2: one sock short settles itself; 4 hats short of 10 is far past 2% and waits
        List<TransferService.Receipt> r = tx.execute(s -> transfers.receive(t, Map.of(socks, q("99"), hats, q("6")), "t"));
        assertEquals("SETTLED_WITHIN_TOLERANCE", r.get(0).outcome());
        assertEquals("WAITING_FOR_DECISION", r.get(1).outcome());
        assertEquals("IXF_COMPLETE", xferStatus(t, 0));
        assertEquals("IXF_RECEIVED_SHORT", xferStatus(t, 1));
        same("99", inventory.atp(socks, b));
        same("6", inventory.atp(hats, b));
        assertEquals("VAR_TRANSIT_LOSS", jdbc.queryForObject("SELECT v.variance_reason_id FROM inventory_item_variance v JOIN inventory_item i "
                + "ON i.inventory_item_id = v.inventory_item_id WHERE i.product_id = ?", String.class, socks));
        assertNotEquals("SHIPMENT_DELIVERED", jdbc.queryForObject("SELECT status_id FROM shipment WHERE shipment_id = ?", String.class, t));

        // the store finds 3 more in the box; 1 is lost
        tx.executeWithoutResult(s -> transfers.settle(t, hats, q("3"), "found 3 under the packing", "manager"));
        same("9", inventory.atp(hats, b));
        assertEquals("IXF_COMPLETE", xferStatus(t, 1));
        assertEquals("SHIPMENT_DELIVERED", jdbc.queryForObject("SELECT status_id FROM shipment WHERE shipment_id = ?", String.class, t));
        same("0", jdbc.queryForObject("SELECT COALESCE(SUM(i.quantity_on_hand_total), 0) FROM inventory_item i WHERE i.facility_id = 'IN_TRANSIT' AND i.product_id IN (?, ?)",
                BigDecimal.class, socks, hats));
    }

    @Test
    void cancellingAnApprovedTransferGivesStockBackAndTheFlowHolds() {
        String hat = product("hat");
        String a = store("A"), b = store("B");
        stock(hat, a, "2");
        String t = tx.execute(s -> transfers.request(a, b, List.of(new TransferService.Line(hat, q("2"))), null, "t"));
        assertThrows(StatusService.StatusTransitionException.class, () -> tx.executeWithoutResult(s -> transfers.ship(t, null, "t")), "approve first");
        tx.executeWithoutResult(s -> transfers.approve(t, "t"));
        same("0", inventory.atp(hat, a));
        tx.executeWithoutResult(s -> transfers.cancel(t, "t"));
        same("2", inventory.atp(hat, a));
        assertEquals("IXF_CANCELLED", xferStatus(t, 0));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> transfers.request(a, a, List.of(new TransferService.Line(hat, q("1"))), null, "t")));
    }

    @Test
    void lookupRanksStoresByConfidence() {
        String p = product("jacket");
        String counted = store("Counted"), one = store("One left"), missed = store("Missed it");
        stock(p, counted, "5");
        stock(p, one, "1");
        stock(p, missed, "5");
        tx.executeWithoutResult(s -> inventory.recordCount(p, counted, q("5"), null, null, "weekly count"));
        // an order item the 'missed' store could not find
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, null, null, null, null, null, null, null, null, null,
                List.of(new OrderService.NewItem(p, "Jacket", BigDecimal.ONE, q("1"), null, null, 0)), null, null, null, null, null, null, "t")));
        tx.executeWithoutResult(s -> reservations.excludeFacility(o, "00001", missed));

        List<StoreNetworkService.Availability> l = network.lookup(p);
        assertEquals(StoreNetworkService.Confidence.HIGH, l.stream().filter(x -> x.facilityId().equals(counted)).findFirst().orElseThrow().confidence());
        StoreNetworkService.Availability m = l.stream().filter(x -> x.facilityId().equals(missed)).findFirst().orElseThrow();
        assertEquals(StoreNetworkService.Confidence.LOW, m.confidence());
        assertTrue(m.reasons().stream().anyMatch(r -> r.startsWith("could not find it")), m.reasons().toString());
        assertEquals(StoreNetworkService.Confidence.LOW, l.stream().filter(x -> x.facilityId().equals(one)).findFirst().orElseThrow().confidence());
        assertEquals(counted, l.get(0).facilityId());
    }

    @Test
    void holdForPickupAndSalesCreditForTheSellingStore() {
        String storeId = "NET-STORE-" + N.incrementAndGet();
        tx.executeWithoutResult(s -> stores.createIfMissing(new ProductStoreService.NewStore(storeId, storeId, "USD", null, "POS_SALES_CHANNEL", null, null)));
        tx.executeWithoutResult(s -> stores.setSetting(storeId, "SALES_CREDIT_SELLING_PCT", "30"));
        String p = product("dress");
        String seller = store("Seller"), keeper = store("Keeper");
        stock(p, keeper, "2");
        String hold = tx.execute(s -> network.holdForPickup(storeId, p, keeper, BigDecimal.ONE, customer, 24, seller, "t"));
        same("1", inventory.atp(p, keeper));
        assertNotNull(jdbc.queryForObject("SELECT auto_cancel_date FROM order_item WHERE order_id = ?", java.sql.Timestamp.class, hold));
        assertEquals("STOREPICKUP", jdbc.queryForObject("SELECT shipment_method_type_id FROM order_item_ship_group WHERE order_id = ?", String.class, hold));

        // endless aisle: sold at the seller, fulfilled by the keeper, 30% credit stays with the seller
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, storeId, "POS_SALES_CHANNEL", "USD", null, null, null, customer, null, null,
                List.of(new OrderService.NewShipGroup("STANDARD", keeper, null, null, null, true, false, null, null, null, null)),
                List.of(new OrderService.NewItem(p, "Dress", BigDecimal.ONE, q("200"), null, null, 0)), null, null, null, null, null, null, "t")));
        tx.executeWithoutResult(s -> network.setSellingFacility(o, seller));
        Map<String, BigDecimal> credit = network.salesCredit(o);
        same("60", credit.get(seller));
        same("140", credit.get(keeper));
    }

    @Test
    void rebalancingMovesSlowStockToWhereItSellsWhenTheMarginPays() {
        String tee = product("tee");
        String slow = store("Slow"), fast = store("Fast");
        stock(tee, slow, "60");
        stock(tee, fast, "2");
        tx.executeWithoutResult(s -> {
            products.setPrice(tee, "DEFAULT_PRICE", "USD", null, q("25"));
            products.setPrice(tee, "AVERAGE_COST", "USD", null, q("10"));
        });
        jdbc.update("UPDATE product_facility SET sales_velocity = ? WHERE product_id = ? AND facility_id = ?", q("0.2"), tee, slow);   // 60 / 1.4 a week: 43 weeks
        jdbc.update("UPDATE product_facility SET sales_velocity = ? WHERE product_id = ? AND facility_id = ?", q("2"), tee, fast);     // 2 / 14 a week: 0.1 weeks
        String cluster = tx.execute(s -> facilities.group("CLUSTER", "Downtown " + N.incrementAndGet()));
        tx.executeWithoutResult(s -> {
            facilities.addToGroup(slow, cluster, 1L);
            facilities.addToGroup(fast, cluster, 2L);
        });
        List<StoreNetworkService.Suggestion> s = network.rebalance(cluster, 2, 12, 4);
        assertEquals(1, s.size(), s.toString());
        StoreNetworkService.Suggestion x = s.get(0);
        assertEquals(slow, x.fromFacilityId());
        assertEquals(fast, x.toFacilityId());
        same("54", x.quantity());          // fast wants 4 weeks x 14 = 56, has 2
        same("15", x.unitMargin());
    }
}
