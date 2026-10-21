package com.nexus.core;

import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.PicklistService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ReturnService;
import com.nexus.core.service.ShipmentService;
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
class FulfilmentReturnTest {

    @Autowired OrderService orders;
    @Autowired ShipmentService shipments;
    @Autowired PicklistService picklists;
    @Autowired ReturnService returns;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired PartyService parties;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String shoe, belt, store, customer, shipTo;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            shoe = products.create(new ProductService.NewProduct(null, "Ship Shoe", null, null, null, null, null, false, false));
            belt = products.create(new ProductService.NewProduct(null, "Ship Belt", null, null, null, null, null, false, false));
            store = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Ship store", null, null, null, null, null, null, null, null, null));
            customer = parties.createPerson(PartyService.PersonName.parse("Grace Hopper"), null);
            shipTo = parties.addPostalAddress(customer, new PartyService.AddressInput("Grace Hopper", "2 Navy Way", null, "Arlington",
                    "VA", "22201", "US", null, null, null), "SHIPPING_LOCATION");
            inventory.receive(shoe, store, new BigDecimal("20"), null, null, null, null);
            inventory.receive(belt, store, new BigDecimal("20"), null, null, null, null);
        });
    }

    private static BigDecimal q(String v) {
        return new BigDecimal(v);
    }

    /** An approved order of 2 shoes at 50 and 1 belt at 20, routed and held at the store; settled payment of 120. */
    private String heldOrder() {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, "WEB_SALES_CHANNEL", "USD", null, null, null, customer,
                null, null, List.of(new OrderService.NewShipGroup("STANDARD", store, shipTo, null, null, true, false, null, null, null, null)),
                List.of(new OrderService.NewItem(shoe, "Shoe", q("2"), q("50"), null, null, 0),
                        new OrderService.NewItem(belt, "Belt", q("1"), q("20"), null, null, 0)),
                null, List.of(new OrderService.NewPayment("CREDIT_CARD", q("120"), "PAYMENT_SETTLED", "ch_1")), null, null, null, null, "t")));
        tx.executeWithoutResult(s -> {
            orders.changeStatus(o, "ORDER_APPROVED", null, "t");
            orders.reserve(o, "00001", store, null, "t");
            orders.reserve(o, "00002", store, null, "t");
        });
        return o;
    }

    private String status(String table, String key, String id) {
        return jdbc.queryForObject("SELECT status_id FROM " + table + " WHERE " + key + " = ?", String.class, id);
    }

    @Test
    void aWmsConfirmationShipsTwoBoxesCompletesTheOrderAndIsIdempotent() {
        String o = heldOrder();
        BigDecimal shoesOnHand = inventory.onHand(shoe, store);
        BigDecimal shoesAtp = inventory.atp(shoe, store);
        ShipmentService.Confirmation c = new ShipmentService.Confirmation("WMS-SH-1", o, "00001", null, "UPS", "STANDARD", List.of(
                new ShipmentService.ConfirmedPackage("1Z1", q("1.2"), null, "BOX_SMALL", Map.of("00001", q("1"))),
                new ShipmentService.ConfirmedPackage("1Z2", q("2.0"), null, "BOX_MEDIUM", Map.of("00001", q("1"), "00002", q("1")))));
        String sh = tx.execute(s -> shipments.confirm(c, "wms"));
        assertEquals(sh, tx.execute(s -> shipments.confirm(c, "wms")), "the same confirmation again is the same shipment");

        assertEquals("SHIPMENT_SHIPPED", shipments.status(sh));
        assertEquals("ORDER_COMPLETED", status("order_header", "order_id", o));
        assertEquals("ITEM_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ? AND order_item_seq_id = '00002'", String.class, o));
        assertEquals(0, shoesOnHand.subtract(q("2")).compareTo(inventory.onHand(shoe, store)), "on hand goes down when it ships");
        assertEquals(0, shoesAtp.compareTo(inventory.atp(shoe, store)), "available already went down when it was held");
        assertEquals(0, BigDecimal.ZERO.compareTo(orders.reservedQuantity(o, "00001")), "the hold is consumed");
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM shipment_package WHERE shipment_id = ?", Long.class, sh));
        assertEquals("1Z2", jdbc.queryForObject("SELECT tracking_code FROM shipment_package_route_seg WHERE shipment_id = ? AND shipment_package_seq_id = '00002'", String.class, sh));
        assertEquals("1Z1", jdbc.queryForObject("SELECT tracking_number FROM order_item_ship_group WHERE order_id = ?", String.class, o));
        assertEquals(0, q("2").compareTo(shipments.issuedQuantity(o, "00001")));
        tx.executeWithoutResult(s -> shipments.deliver(sh, null, "t"));
        assertEquals("SHIPMENT_DELIVERED", shipments.status(sh));
    }

    @Test
    void partialShipmentLeavesTheRestOpenAndRejectsOverShipping() {
        String o = heldOrder();
        String first = tx.execute(s -> shipments.createSalesShipment(o, "00001", Map.of("00001", q("1")), "t"));
        tx.executeWithoutResult(s -> shipments.ship(first, "FEDEX", "STANDARD", Map.of("00001", "FX1"), null, "t"));
        assertEquals("ORDER_APPROVED", status("order_header", "order_id", o));
        assertEquals("ITEM_APPROVED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ? AND order_item_seq_id = '00001'", String.class, o));
        assertEquals(0, q("1").compareTo(orders.reservedQuantity(o, "00001")));
        IllegalArgumentException over = assertThrows(IllegalArgumentException.class,
                () -> tx.execute(s -> shipments.createSalesShipment(o, "00001", Map.of("00001", q("2")), "t")));
        assertTrue(over.getMessage().contains("has 1 left to ship"), over.getMessage());
        String rest = tx.execute(s -> shipments.createSalesShipment(o, "00001", null, "t"));
        tx.executeWithoutResult(s -> shipments.ship(rest, "FEDEX", "STANDARD", null, null, "t"));
        assertEquals("ORDER_COMPLETED", status("order_header", "order_id", o));
    }

    @Test
    void aStorePicklistShipsWhatWasFoundAndShortPicksTheRest() {
        String o = heldOrder();
        BigDecimal beltAtp = inventory.atp(belt, store);
        String p = tx.execute(s -> picklists.create(store, "STANDARD", List.of(new PicklistService.GroupRef(o, "00001")), "t"));
        assertEquals(2, picklists.items(p).size());
        tx.executeWithoutResult(s -> {
            picklists.assign(p, customer, "t");
            picklists.recordPick(p, o, "00001", q("2"));
            picklists.recordPick(p, o, "00002", q("0"));   // the belt isn't on the shelf
        });
        List<String> shipped = tx.execute(s -> picklists.complete(p, "_NA_", "STANDARD", "t"));
        assertEquals(1, shipped.size());
        assertEquals("PICKLIST_COMPLETED", status("picklist", "picklist_id", p));
        assertEquals("ITEM_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ? AND order_item_seq_id = '00001'", String.class, o));
        assertEquals("ITEM_APPROVED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ? AND order_item_seq_id = '00002'", String.class, o));
        assertEquals(0, BigDecimal.ZERO.compareTo(orders.reservedQuantity(o, "00002")), "the belt's hold is released");
        assertEquals(0, beltAtp.add(q("1")).compareTo(inventory.atp(belt, store)));
        assertEquals(store, orders.itemAttribute(o, "00002", "excludedFacilityIds"), "routing won't send it back here");
        assertEquals("ORDER_APPROVED", status("order_header", "order_id", o), "the belt still has to ship from somewhere");
    }

    @Test
    void returnReceiveRestockHoldAndRefund() {
        String o = heldOrder();
        String sh = tx.execute(s -> shipments.createSalesShipment(o, "00001", null, "t"));
        tx.executeWithoutResult(s -> shipments.ship(sh, "UPS", "STANDARD", null, null, "t"));
        BigDecimal shoeAtp = inventory.atp(shoe, store);
        BigDecimal shoeOnHand = inventory.onHand(shoe, store);

        IllegalArgumentException tooMany = assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> returns.create(o,
                List.of(new ReturnService.NewReturnItem("00001", q("3"), "RTN_SIZE_FIT", null)), store, null, "t")));
        assertTrue(tooMany.getMessage().contains("can return up to 2"), tooMany.getMessage());

        String r = tx.execute(s -> returns.create(o, List.of(new ReturnService.NewReturnItem("00001", q("2"), "RTN_SIZE_FIT", "RTN_REFUND"),
                new ReturnService.NewReturnItem("00002", q("1"), "RTN_DAMAGED", "RTN_REFUND")), store, "RMA-1", "t"));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> returns.create(o,
                List.of(new ReturnService.NewReturnItem("00001", q("1"), null, null)), store, null, "t")), "both shoes are already on a return");
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> returns.receive(r, "00001", q("1"), null, null, null, "t")), "accept first");

        tx.executeWithoutResult(s -> {
            returns.accept(r, "t");
            returns.receive(r, "00001", q("1"), null, null, ReturnService.Grade.RESELLABLE, "t");
            returns.receive(r, "00001", q("0"), q("1"), "SRJ_WRONG_ITEM", null, "t");    // a different shoe came back
            returns.receive(r, "00002", q("1"), null, null, ReturnService.Grade.DAMAGED, "t");
            returns.addAdjustment(r, "RESTOCKING_FEE", q("-5"), "Restocking");
        });
        assertEquals("RETURN_RECEIVED", status("return_header", "return_id", r));
        assertEquals(0, shoeAtp.add(q("1")).compareTo(inventory.atp(shoe, store)), "one resellable shoe back on sale");
        assertEquals(0, shoeOnHand.add(q("1")).compareTo(inventory.onHand(shoe, store)));
        // 1 shoe at 50 + 1 belt at 20 - 5 restocking
        assertEquals(0, q("65").compareTo(returns.refundAmount(r)));

        String response = tx.execute(s -> returns.complete(r, "re_1", "t"));
        assertEquals("RETURN_COMPLETED", status("return_header", "return_id", r));
        assertEquals(0, q("65").compareTo(jdbc.queryForObject("SELECT response_amount FROM return_item_response WHERE return_item_response_id = ?", BigDecimal.class, response)));
        assertEquals("PAYMENT_SETTLED", jdbc.queryForObject("SELECT status_id FROM order_payment_preference WHERE order_id = ?", String.class, o), "a part refund keeps the payment settled");
    }
}
