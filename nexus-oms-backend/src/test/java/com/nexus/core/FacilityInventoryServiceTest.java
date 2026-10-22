package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.StatusService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacilityInventoryServiceTest {

    @Autowired FacilityService facilities;
    @Autowired InventoryService inventory;
    @Autowired ProductService products;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
    }

    private String product(String name) {
        return tx.execute(s -> products.create(new ProductService.NewProduct(null, name, null, null, null, null, null, false, false)));
    }

    private String facility(String name, String type, Long dailyLimit) {
        return tx.execute(s -> facilities.create(new FacilityService.NewFacility(null, type, name, null, null, null, null, null,
                null, null, null, dailyLimit)));
    }

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    private static void same(String expected, BigDecimal actual) {
        assertEquals(0, q(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void receiveReserveShipAndCountKeepEveryChange() {
        String boot = product("Boot");
        String dc = facility("North DC", "DISTRIBUTION_CENTER", null);
        tx.executeWithoutResult(s -> inventory.receive(boot, dc, q("10"), q("40.00"), "USD", null, InventoryService.Cause.note("PO 1")));
        tx.executeWithoutResult(s -> inventory.reserve(boot, dc, q("3"), InventoryService.Cause.order("O100", "00001")));
        same("10", inventory.onHand(boot, dc));
        same("7", inventory.atp(boot, dc));

        InventoryService.InsufficientStockException e = assertThrows(InventoryService.InsufficientStockException.class,
                () -> tx.executeWithoutResult(s -> inventory.reserve(boot, dc, q("8"), InventoryService.Cause.order("O101", "00001"))));
        assertTrue(e.getMessage().startsWith("Only 7 of product " + boot), e.getMessage());

        tx.executeWithoutResult(s -> inventory.ship(boot, dc, q("3"), InventoryService.Cause.order("O100", "00001")));
        same("7", inventory.onHand(boot, dc));
        same("7", inventory.atp(boot, dc));

        String count = tx.execute(s -> inventory.recordCount(boot, dc, q("6"), "VAR_DAMAGED", null, "one scuffed"));
        same("6", inventory.onHand(boot, dc));
        same("6", inventory.atp(boot, dc));
        same("-1", jdbc.queryForObject("SELECT quantity_on_hand_var FROM inventory_item_variance WHERE physical_inventory_id = ?", BigDecimal.class, count));
        assertEquals("VAR_DAMAGED", jdbc.queryForObject("SELECT variance_reason_id FROM inventory_item_variance WHERE physical_inventory_id = ?", String.class, count));

        List<String> reasons = jdbc.queryForList("SELECT d.reason_enum_id FROM inventory_item_detail d JOIN inventory_item i "
                + "ON i.inventory_item_id = d.inventory_item_id WHERE i.product_id = ? ORDER BY d.effective_date", String.class, boot);
        assertEquals(List.of("IID_RECEIPT", "IID_RESERVE", "IID_SHIPMENT", "IID_COUNT"), reasons, "the failed reservation left no row");
        same("6", jdbc.queryForObject("SELECT SUM(d.quantity_on_hand_diff) FROM inventory_item_detail d JOIN inventory_item i "
                + "ON i.inventory_item_id = d.inventory_item_id WHERE i.product_id = ?", BigDecimal.class, boot));
        assertEquals("O100", jdbc.queryForObject("SELECT d.order_id FROM inventory_item_detail d JOIN inventory_item i "
                + "ON i.inventory_item_id = d.inventory_item_id WHERE i.product_id = ? AND d.reason_enum_id = 'IID_SHIPMENT'", String.class, boot));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_item WHERE product_id = ?", Long.class, boot), "bulk stock is one item");
    }

    @Test
    void productFacilityFollowsAvailableLessSafetyStock() {
        String sock = product("Sock");
        String store = facility("Mall store", "RETAIL_STORE", null);
        tx.executeWithoutResult(s -> {
            inventory.receive(sock, store, q("5"), null, null, null, null);
            inventory.setLevels(sock, store, new InventoryService.Levels(q("2"), q("3"), q("12"), null, true, false));
        });
        same("5", jdbc.queryForObject("SELECT last_inventory_count FROM product_facility WHERE product_id = ? AND facility_id = ?", BigDecimal.class, sock, store));
        same("3", jdbc.queryForObject("SELECT computed_last_inventory_count FROM product_facility WHERE product_id = ? AND facility_id = ?", BigDecimal.class, sock, store));
        tx.executeWithoutResult(s -> inventory.reserve(sock, store, q("4"), null));
        same("1", jdbc.queryForObject("SELECT last_inventory_count FROM product_facility WHERE product_id = ? AND facility_id = ?", BigDecimal.class, sock, store));
        same("0", jdbc.queryForObject("SELECT computed_last_inventory_count FROM product_facility WHERE product_id = ? AND facility_id = ?", BigDecimal.class, sock, store));
        assertEquals("N", jdbc.queryForObject("SELECT allow_pickup FROM product_facility WHERE product_id = ? AND facility_id = ?", String.class, sock, store));
    }

    @Test
    void dailyOrderLimitStopsAtTheLimitAndGivesPlacesBack() {
        String store = facility("Small store", "RETAIL_STORE", 2L);
        String dc = facility("Big DC", "DISTRIBUTION_CENTER", null);
        LocalDate day = LocalDate.of(2026, 10, 21);
        assertTrue(take(store, day));
        assertTrue(take(store, day));
        assertFalse(take(store, day), "full for the day");
        assertEquals(Optional.of(0L), facilities.ordersLeft(store, day));
        assertEquals(Optional.of(2L), facilities.ordersLeft(store, day.plusDays(1)), "a new day starts empty");
        tx.executeWithoutResult(s -> facilities.releaseOrder(store, day));
        assertEquals(Optional.of(1L), facilities.ordersLeft(store, day));
        assertTrue(take(dc, day));
        assertEquals(Optional.empty(), facilities.ordersLeft(dc, day), "no limit");
    }

    @Test
    void groupsKeepTheirOrderAndStoresTheirFacilities() {
        String a = facility("Group A1", "RETAIL_STORE", null);
        String b = facility("Group A2", "RETAIL_STORE", null);
        String g = tx.execute(s -> facilities.group("BROKERING_GROUP", "Metro stores"));
        assertEquals(g, tx.execute(s -> facilities.group("BROKERING_GROUP", "Metro stores")), "one group per type and name");
        tx.executeWithoutResult(s -> {
            facilities.addToGroup(b, g, 2L);
            facilities.addToGroup(a, g, 1L);
            facilities.addToGroup(a, g, 5L);   // already a member: keeps its place
            facilities.setIdentification(a, "POS_LOCATION_ID", "POS-17");
        });
        assertEquals(List.of(a, b), facilities.members(g));
        tx.executeWithoutResult(s -> facilities.removeFromGroup(a, g));
        assertEquals(List.of(b), facilities.members(g));
        assertEquals(Optional.of(a), facilities.findByIdentification("POS_LOCATION_ID", "POS-17"));

        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class,
                () -> tx.execute(s -> facilities.create(new FacilityService.NewFacility("bad id!", null, "X", null, null, null, null, null, null, null, null, null))));
        assertTrue(bad.getMessage().startsWith("A facility id is"));
    }

    private boolean take(String facilityId, LocalDate day) {
        Boolean taken = tx.execute(s -> facilities.takeOrder(facilityId, day));
        return Boolean.TRUE.equals(taken);
    }

}
