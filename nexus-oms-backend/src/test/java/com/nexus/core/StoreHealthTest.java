package com.nexus.core;

import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.RoutingSetupService.Cond;
import com.nexus.core.service.StoreHealthService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreHealthTest {

    @Autowired StoreHealthService health;
    @Autowired RoutingService routing;
    @Autowired com.nexus.core.service.RoutingSetupService routingSetup;
    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired ProductStoreService stores;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String store = "HEALTH-STORE", shaky, steady, boot, belt;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            stores.createIfMissing(new ProductStoreService.NewStore(store, store, "USD", null, "WEB_SALES_CHANNEL", null, null));
            shaky = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Shaky store", null, null, null, null, null, null, null, null, null));
            steady = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Steady store", null, null, null, null, null, null, null, null, null));
            boot = products.create(new ProductService.NewProduct(null, "Health Boot", null, null, null, null, null, false, false));
            belt = products.create(new ProductService.NewProduct(null, "Health Belt", null, null, null, null, null, false, false));
            inventory.receive(boot, shaky, new BigDecimal("30"), null, null, null, null);
            inventory.receive(boot, steady, new BigDecimal("30"), null, null, null, null);
            inventory.receive(belt, steady, new BigDecimal("8"), null, null, null, null);
            inventory.recordCount(belt, steady, new BigDecimal("8"), null, null, "weekly count");
        });
        // four orders each sent to both stores by hand; the shaky store fails to find two of its four
        for (int i = 0; i < 4; i++) {
            String a = order(), b = order();
            routing.reallocate(a, "00001", shaky, "test", "t");
            routing.reallocate(b, "00001", steady, "test", "t");
            if (i < 2) tx.executeWithoutResult(s -> reservations.shortPick(a, "00001", shaky, BigDecimal.ONE, "not on the shelf", "t"));
        }
    }

    private String order() {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, store, "WEB_SALES_CHANNEL", "USD", null, null, null, null, null, null, null,
                List.of(new OrderService.NewItem(boot, "Boot", BigDecimal.ONE, BigDecimal.TEN, null, null, 0)), null, null, null, null, null, null, "t")));
        tx.executeWithoutResult(s -> orders.changeStatus(o, "ORDER_APPROVED", null, "t"));
        return o;
    }

    @Test
    void reliabilityIsSmoothedAndExplained() {
        Map<String, StoreHealthService.Reliability> r = new java.util.HashMap<>();
        health.reliability(90).forEach(x -> r.put(x.facilityId(), x));
        StoreHealthService.Reliability s = r.get(shaky), t = r.get(steady);
        assertEquals(4, s.ordersRouted());
        assertEquals(2, s.pickFailures());
        assertEquals(0.786, s.score(), 0.001, "(2 picked + 9) / (4 + 10)");
        assertTrue(t.score() > 0.92 && t.pickFailures() == 0, "steady: every routed order picked; " + t);
        assertTrue(s.reason().startsWith("2 pick failure(s) against 4"));
    }

    @Test
    void routingCanPreferTheReliableStore() {
        String o = order();
        String g = routingSetup.createGroup(store, "Reliable first", null);
        String r = routingSetup.addRouting(g, "Queue", 1, List.of(Cond.filter("facilityId", "in", RoutingService.QUEUE)));
        routingSetup.addRule(r, "Most reliable", 1, false, List.of(Cond.filter("facilityId", "in", shaky + "," + steady), Cond.sortBy("reliability", "desc")), Map.of());
        routingSetup.activate(r);
        RoutingService.RunResult res = routing.run(g, "t");
        assertEquals(steady, jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, o));
        assertTrue(res.decisions().stream().filter(d -> d.orderId().equals(o)).findFirst().orElseThrow().explanation().contains("picks 93% of its orders") || res.decisions().get(0).explanation().contains("picks 9"));
    }

    @Test
    void phantomStockRanksWhatACountShouldCheck() {
        List<StoreHealthService.PhantomRisk> risks = health.phantomStock(null, 30, 1000);
        StoreHealthService.PhantomRisk shakyBoot = risks.stream().filter(x -> x.facilityId().equals(shaky) && x.productId().equals(boot)).findFirst().orElseThrow();
        assertTrue(shakyBoot.risk() >= 0.6, shakyBoot.toString());
        assertTrue(shakyBoot.reasons().get(0).startsWith("could not be found 2 time(s)"), shakyBoot.reasons().toString());
        assertTrue(risks.stream().noneMatch(x -> x.facilityId().equals(steady) && x.productId().equals(belt)), "a clean recent count clears it");
        int shakyIndex = risks.indexOf(shakyBoot);
        risks.stream().filter(x -> x.facilityId().equals(steady) && x.productId().equals(boot)).findFirst()
                .ifPresent(x -> assertTrue(risks.indexOf(x) > shakyIndex, "the store that failed ranks first"));
        assertEquals(List.of(shakyBoot.productId()), health.phantomStock(shaky, 30, 10).stream().filter(x -> x.productId().equals(boot)).map(StoreHealthService.PhantomRisk::productId).toList());
    }
}
