package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.RoutingSetupService.Cond;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoutingServiceTest {

    @Autowired RoutingService routing;
    @Autowired com.nexus.core.service.RoutingSetupService routingSetup;
    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired ProductStoreService stores;
    @Autowired PartyService parties;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    static final AtomicInteger N = new AtomicInteger();
    String customer, hoboken;   // ship-to in Hoboken, NJ

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            customer = parties.createPerson(PartyService.PersonName.parse("Route Tester"), null);
            hoboken = parties.addPostalAddress(customer, new PartyService.AddressInput("Route Tester", "1 Hudson St", null, "Hoboken", "NJ", "07030", "US",
                    new BigDecimal("40.7440"), new BigDecimal("-74.0324"), true), "SHIPPING_LOCATION");
        });
    }

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    private String store(boolean allowSplit) {
        String id = "RT-STORE-" + N.incrementAndGet();
        tx.executeWithoutResult(s -> stores.createIfMissing(new ProductStoreService.NewStore(id, id, "USD", null, "WEB_SALES_CHANNEL", null, null)));
        assertEquals(1, jdbc.update("UPDATE product_store SET allow_split = ? WHERE product_store_id = ?", allowSplit ? "Y" : "N", id));
        return id;
    }

    private String product(String name) {
        return tx.execute(s -> products.create(new ProductService.NewProduct(null, name + "-" + N.incrementAndGet(), null, null, null, null, null, false, false)));
    }

    private String place(String name, String type, double lat, double lon, Long limit) {
        return tx.execute(s -> facilities.create(new FacilityService.NewFacility(null, type, name + "-" + N.incrementAndGet(), null, null, null,
                BigDecimal.valueOf(lat), BigDecimal.valueOf(lon), null, null, null, limit)));
    }

    private void stock(String product, String facility, String qty) {
        tx.executeWithoutResult(s -> inventory.receive(product, facility, q(qty), null, null, null, null));
    }

    /** An approved order at the store for the products, one each, waiting to be routed. */
    private String order(String storeId, String method, String... productIds) {
        List<OrderService.NewItem> items = new java.util.ArrayList<>();
        for (String p : productIds) items.add(new OrderService.NewItem(p, p, BigDecimal.ONE, q("10"), null, null, 0));
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, storeId, "WEB_SALES_CHANNEL", "USD", null, null, null, customer, null, null,
                List.of(new OrderService.NewShipGroup(method, null, hoboken, null, null, true, false, null, null, null, null)), items,
                null, null, null, null, null, null, "t")));
        tx.executeWithoutResult(s -> orders.changeStatus(o, "ORDER_APPROVED", null, "t"));
        return o;
    }

    /** A group with one active routing over the queue and the given rules (each: split?, conditions, actions). */
    private String group(String storeId, List<Object[]> rules) {
        String g = routingSetup.createGroup(storeId, "Default", 15L);
        String r = routingSetup.addRouting(g, "All web orders", 1, List.of(Cond.filter("facilityId", "in", RoutingService.QUEUE), Cond.sortBy("orderDate", "asc")));
        int seq = 1;
        for (Object[] rule : rules) {
            @SuppressWarnings("unchecked") List<Cond> conds = (List<Cond>) rule[1];
            @SuppressWarnings("unchecked") Map<String, String> actions = (Map<String, String>) rule[2];
            routingSetup.addRule(r, "Rule " + seq, seq++, (Boolean) rule[0], conds, actions);
        }
        routingSetup.activate(r);
        return g;
    }

    private String facilityOf(String orderId, String group) {
        return jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ? AND ship_group_seq_id = ?", String.class, orderId, group);
    }

    @Test
    void oneLocationWithEverythingBeatsANearerOneWithPart() {
        String st = store(true);
        String shoe = product("shoe"), sock = product("sock");
        String near = place("Hoboken store", "RETAIL_STORE", 40.745, -74.03, null);       // next door, only the shoe
        String far = place("Edison DC", "DISTRIBUTION_CENTER", 40.52, -74.41, null);      // ~40 km, both
        stock(shoe, near, "5");
        stock(shoe, far, "5");
        stock(sock, far, "5");
        String o = order(st, "STANDARD", shoe, sock);
        String g = group(st, List.<Object[]>of(new Object[]{true, List.of(Cond.sortBy("distance", "asc")), Map.of()}));

        RoutingService.RunResult r = routing.run(g, "t");
        assertEquals(1, r.routed());
        assertEquals(0, r.split());
        assertEquals(far, facilityOf(o, "00001"));
        assertEquals(0, q("1").compareTo(reservations.reservedQuantity(o, "00002")));
        String why = jdbc.queryForObject("SELECT comments FROM order_facility_change WHERE order_id = ?", String.class, o);
        assertTrue(why.contains("holds every item") && why.contains("km away"), why);
        assertEquals(1L, jdbc.queryForObject("SELECT orders_routed FROM order_routing_run WHERE routing_run_id = ?", Long.class, r.routingRunId()));
        assertEquals(0, routing.run(g, "t").considered(), "a routed order isn't routed again");
    }

    @Test
    void splitsOverTheFewestLocationsWhenNoneHasEverything() {
        String st = store(true);
        String a = product("a"), b = product("b"), c = product("c");
        String one = place("One", "WAREHOUSE", 40.7, -74.0, null), two = place("Two", "WAREHOUSE", 40.8, -74.1, null);
        stock(a, one, "3");
        stock(b, one, "3");
        stock(c, two, "3");
        String o = order(st, "STANDARD", a, b, c);
        String g = group(st, List.<Object[]>of(new Object[]{true, List.of(), Map.of()}));
        RoutingService.RunResult r = routing.run(g, "t");
        assertEquals(1, r.split());
        assertEquals(one, facilityOf(o, "00001"), "the location with two items keeps the first shipment");
        assertEquals(two, facilityOf(o, "00002"));
        assertEquals("00002", jdbc.queryForObject("SELECT ship_group_seq_id FROM order_item WHERE order_id = ? AND product_id = ?", String.class, o, c));
    }

    @Test
    void aStoreThatForbidsSplitsParksTheOrder() {
        String st = store(false);
        String a = product("a"), b = product("b");
        String one = place("One", "WAREHOUSE", 40.7, -74.0, null), two = place("Two", "WAREHOUSE", 40.8, -74.1, null);
        stock(a, one, "3");
        stock(b, two, "3");
        String o = order(st, "STANDARD", a, b);
        String g = group(st, List.<Object[]>of(new Object[]{true, List.of(), Map.of("ORA_MV_TO_QUEUE", "UNFILLABLE_PARKING", "ORA_AUTO_CANCEL_DAYS", "7")}));
        RoutingService.RunResult r = routing.run(g, "t");
        assertEquals(1, r.unfillable(), r.toString());
        assertEquals("UNFILLABLE_PARKING", facilityOf(o, "00001"));
        assertNotNull(jdbc.queryForObject("SELECT auto_cancel_date FROM order_item WHERE order_id = ? AND order_item_seq_id = '00001'", java.sql.Timestamp.class, o));
        assertEquals(0, BigDecimal.ZERO.compareTo(reservations.reservedQuantity(o, "00001")), "parking holds no stock");
    }

    @Test
    void fullLocationsAndExcludedLocationsAreSkippedAndTheNextRuleTries() {
        String st = store(true);
        String p = product("p");
        String full = place("Full store", "RETAIL_STORE", 40.745, -74.03, 1L);
        String rejected = place("Rejected store", "RETAIL_STORE", 40.746, -74.031, null);
        String dc = place("DC", "DISTRIBUTION_CENTER", 40.0, -75.0, null);
        stock(p, full, "5");
        stock(p, rejected, "5");
        stock(p, dc, "5");
        tx.executeWithoutResult(s -> assertTrue(facilities.takeOrder(full, LocalDate.now(java.time.ZoneOffset.UTC))));   // its one order for today
        String o = order(st, "STANDARD", p);
        tx.executeWithoutResult(s -> reservations.excludeFacility(o, "00001", rejected));
        // rule 1: stores only; rule 2: anything
        String g = group(st, List.<Object[]>of(
                new Object[]{false, List.of(Cond.filter("facilityTypeId", "in", "RETAIL_STORE"), Cond.sortBy("distance", "asc")), Map.of("ORA_NEXT_RULE", "")},
                new Object[]{false, List.of(Cond.sortBy("distance", "asc")), Map.of()}));
        routing.run(g, "t");
        assertEquals(dc, facilityOf(o, "00001"), "the full store and the store that rejected it are skipped");
    }

    @Test
    void substitutesWhenTheOrderedProductIsOutEverywhere() {
        String st = store(true);
        String red = product("red mug"), crimson = product("crimson mug");
        String wh = place("WH", "WAREHOUSE", 40.7, -74.0, null);
        stock(crimson, wh, "4");
        tx.executeWithoutResult(s -> products.associate(red, crimson, "PRODUCT_SUBSTITUTE", 1L, null));
        String o = order(st, "STANDARD", red);
        String g = group(st, List.<Object[]>of(new Object[]{false, List.of(Cond.filter("allowSubstitute", "=", "Y")), Map.of()}));
        RoutingService.RunResult r = routing.run(g, "t");
        assertEquals(1, r.substituted());
        assertEquals(wh, facilityOf(o, "00001"));
        assertEquals(crimson, jdbc.queryForObject("SELECT product_id FROM order_item WHERE order_id = ?", String.class, o));
        assertEquals(red, orders.itemAttribute(o, "00001", "substitutedFor"));
    }

    @Test
    void sortsProtectFastSellersAndCompleteSizeRuns() {
        String st = store(true);
        String style = product("boot style");
        String s8 = product("boot 8"), s9 = product("boot 9"), s10 = product("boot 10");
        tx.executeWithoutResult(s -> {
            products.associate(style, s8, "PRODUCT_VARIANT", 1L, null);
            products.associate(style, s9, "PRODUCT_VARIANT", 2L, null);
            products.associate(style, s10, "PRODUCT_VARIANT", 3L, null);
        });
        String fullRun = place("Full run", "RETAIL_STORE", 40.745, -74.03, null);
        String broken = place("Broken run", "RETAIL_STORE", 40.80, -74.10, null);
        stock(s8, fullRun, "2");
        stock(s9, fullRun, "2");
        stock(s10, fullRun, "2");
        stock(s9, broken, "2");                        // only size 9 left there
        String o = order(st, "STANDARD", s9);
        String g = group(st, List.<Object[]>of(new Object[]{false, List.of(Cond.sortBy("brokenSizeRun", "asc"), Cond.sortBy("distance", "asc")), Map.of()}));
        routing.run(g, "t");
        assertEquals(broken, facilityOf(o, "00001"), "the store whose run is already broken gives the size, though farther");

        String tee = product("tee");
        String busy = place("Busy", "RETAIL_STORE", 40.745, -74.03, null), quiet = place("Quiet", "RETAIL_STORE", 40.9, -74.2, null);
        stock(tee, busy, "5");
        stock(tee, quiet, "5");
        jdbc.update("UPDATE product_facility SET sales_velocity = ? WHERE product_id = ? AND facility_id = ?", q("4"), tee, busy);
        jdbc.update("UPDATE product_facility SET sales_velocity = ? WHERE product_id = ? AND facility_id = ?", q("0.2"), tee, quiet);
        String o2 = order(st, "STANDARD", tee);
        String g2 = group(st, List.<Object[]>of(new Object[]{false, List.of(Cond.sortBy("salesVelocity", "asc")), Map.of()}));
        routing.run(g2, "t");
        assertEquals(quiet, facilityOf(o2, "00001"), "the slow seller gives, protecting the fast one");
    }

    @Test
    void aSimulationReportsAndChangesNothing() {
        String st = store(true);
        String p = product("p");
        String wh = place("WH", "WAREHOUSE", 40.7, -74.0, null);
        stock(p, wh, "2");
        String o = order(st, "STANDARD", p);
        String g = group(st, List.<Object[]>of(new Object[]{false, List.of(), Map.of()}));
        BigDecimal atp = inventory.atp(p, wh);
        RoutingService.RunResult sim = routing.simulate(g, "t");
        assertTrue(sim.simulation());
        assertEquals(1, sim.routed());
        assertEquals(List.of(wh), sim.decisions().get(0).facilityIds());
        assertNull(facilityOf(o, "00001"));
        assertEquals(0, atp.compareTo(inventory.atp(p, wh)));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM order_facility_change WHERE order_id = ?", Long.class, o));
        assertEquals("Y", jdbc.queryForObject("SELECT is_simulation FROM order_routing_run WHERE routing_run_id = ?", String.class, sim.routingRunId()));
    }

    @Test
    void movesByHandAndInBulk() {
        String st = store(true);
        String p = product("p");
        String a = place("A", "RETAIL_STORE", 40.7, -74.0, null), b = place("B", "RETAIL_STORE", 40.8, -74.1, 0L);   // B takes no new routing
        stock(p, a, "5");
        stock(p, b, "5");
        String o = order(st, "STANDARD", p);
        String g = group(st, List.<Object[]>of(new Object[]{false, List.of(), Map.of()}));
        routing.run(g, "t");
        assertEquals(a, facilityOf(o, "00001"), "a location with a limit of 0 gets no routed orders but keeps selling its stock online");

        BigDecimal atpA = inventory.atp(p, a);
        routing.reallocate(o, "00001", b, "customer will collect at B", "manager");
        assertEquals(b, facilityOf(o, "00001"), "a person may still choose it");
        assertEquals(0, atpA.add(BigDecimal.ONE).compareTo(inventory.atp(p, a)), "the hold at A is released");

        assertEquals(1, routing.moveAllOff(b, "B closed for a stocktake", "manager"));
        assertNull(facilityOf(o, "00001"), "back in the queue");
        assertEquals(java.util.Set.of(b), reservations.excludedFacilities(o, "00001"));
        routing.run(g, "t");
        assertEquals(a, facilityOf(o, "00001"));
        assertEquals(List.of("FCR_ROUTED", "FCR_MANUAL", "FCR_BULK", "FCR_ROUTED"),
                routing.history(o).stream().map(com.nexus.core.model.routing.OrderFacilityChange::getChangeReasonEnumId).toList());
    }

    @Test
    void rejectsUnknownFieldsAndActions() {
        String st = store(true);
        String g = routingSetup.createGroup(st, "x", null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> routingSetup.addRouting(g, "x", 1, List.of(Cond.filter("colour", "=", "red"))));
        assertTrue(e.getMessage().startsWith("Can't filter on colour"));
        String r = routingSetup.addRouting(g, "x", 1, List.of());
        assertThrows(IllegalArgumentException.class, () -> routingSetup.addRule(r, "x", 1, false, List.of(), Map.of("ORA_PANIC", "")));
        double km = kmProbe();
        assertTrue(km > 38 && km < 43, "Hoboken to Edison is about 40 km, got " + km);
    }

    @Test
    void aScheduledGroupIsDueUntilItRunsAndSimulationsDoNotCount() {
        String st = store(true);
        String g = group(st, List.<Object[]>of(new Object[]{false, List.of(), Map.of()}));   // runs every 15 minutes
        java.time.LocalDateTime now = com.nexus.core.service.CoreClock.now();
        assertTrue(routing.scheduledGroupsDue(now).contains(g), "never ran, so it is due");
        routing.simulate(g, "t");
        assertTrue(routing.scheduledGroupsDue(now).contains(g), "a simulation is not a run");
        routing.run(g, "t");
        assertFalse(routing.scheduledGroupsDue(com.nexus.core.service.CoreClock.now()).contains(g), "just ran");
        assertTrue(routing.scheduledGroupsDue(com.nexus.core.service.CoreClock.now().plusMinutes(16)).contains(g), "due again after its 15 minutes");
    }

    private static double kmProbe() {
        // Hoboken to Edison is about 40 km
        return com.nexus.core.service.GeoDistance.km(40.744, -74.0324, 40.52, -74.41);
    }
}
