package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.PartyService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderServiceTest {

    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired PartyService parties;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String boot, sock, dc, customer, shipTo;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            boot = products.create(new ProductService.NewProduct(null, "Order Boot", null, null, null, null, null, false, false));
            sock = products.create(new ProductService.NewProduct(null, "Order Sock", null, null, null, null, null, false, false));
            dc = facilities.create(new FacilityService.NewFacility(null, "DISTRIBUTION_CENTER", "Order DC", null, null, null, null, null, null, null, null, null));
            customer = parties.createPerson(PartyService.PersonName.parse("Ada Lovelace"), null);
            shipTo = parties.addPostalAddress(customer, new PartyService.AddressInput("Ada Lovelace", "1 Main St", null, "Hoboken",
                    "NJ", "07030", "US", null, null, null), "SHIPPING_LOCATION");
            inventory.receive(boot, dc, new BigDecimal("10"), null, null, null, null);
            inventory.receive(sock, dc, new BigDecimal("10"), null, null, null, null);
        });
    }

    private String order() {
        return tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, "WEB_SALES_CHANNEL", "USD", null, "1001", "#1001",
                customer, null, null, List.of(OrderService.NewShipGroup.standard(shipTo)),
                List.of(new OrderService.NewItem(boot, "Boot", new BigDecimal("2"), new BigDecimal("100.00"), null, null, 0),
                        new OrderService.NewItem(sock, "Sock", new BigDecimal("3"), new BigDecimal("5.00"), null, null, 0)),
                List.of(new OrderService.NewAdjustment("SHIPPING_CHARGES", new BigDecimal("7.50"), null, "Shipping"),
                        new OrderService.NewAdjustment("PROMOTION_ADJUSTMENT", new BigDecimal("-10.00"), 0, "10 off boots")),
                List.of(new OrderService.NewPayment("EXT_SHOPIFY", null, "PAYMENT_AUTHORIZED", "pay_1")),
                Map.of("SHOPIFY_ORD_ID", "450789469"), Map.of("source", "test"), null, null, "tester")));
    }

    private BigDecimal total(String orderId) {
        return jdbc.queryForObject("SELECT grand_total FROM order_header WHERE order_id = ?", BigDecimal.class, orderId);
    }

    private String status(String orderId, String item) {
        return item == null
                ? jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, orderId)
                : jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ? AND order_item_seq_id = ?", String.class, orderId, item);
    }

    @Test
    void createsAnOrderWithItsPartsAndTotal() {
        String o = order();
        // 2 x 100 + 3 x 5 + 7.50 shipping - 10 promotion
        assertEquals(0, new BigDecimal("212.50").compareTo(total(o)));
        assertEquals("ORDER_CREATED", status(o, null));
        assertEquals("ITEM_CREATED", status(o, "00001"));
        assertEquals(Optional.of(o), orders.findByIdentification("SHOPIFY_ORD_ID", "450789469"));
        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM order_role WHERE order_id = ? AND party_id = ?", Long.class, o, customer));
        assertEquals(shipTo, jdbc.queryForObject("SELECT contact_mech_id FROM order_item_ship_group WHERE order_id = ?", String.class, o));
        assertEquals("PAYMENT_AUTHORIZED", jdbc.queryForObject("SELECT status_id FROM order_payment_preference WHERE order_id = ?", String.class, o));
        // header, two items, payment
        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM order_status WHERE order_id = ?", Long.class, o));
    }

    @Test
    void approveReserveCancelPartThenCancelAllGivesStockBack() {
        String o = order();
        tx.executeWithoutResult(s -> orders.changeStatus(o, "ORDER_APPROVED", null, "tester"));
        assertEquals("ITEM_APPROVED", status(o, "00002"));

        BigDecimal before = inventory.atp(boot, dc);
        tx.executeWithoutResult(s -> reservations.reserve(o, "00001", dc, null, "tester"));
        assertEquals(0, before.subtract(new BigDecimal("2")).compareTo(inventory.atp(boot, dc)));
        assertEquals(0, new BigDecimal("2").compareTo(reservations.reservedQuantity(o, "00001")));
        IllegalArgumentException over = assertThrows(IllegalArgumentException.class,
                () -> tx.executeWithoutResult(s -> reservations.reserve(o, "00001", dc, BigDecimal.ONE, "tester")));
        assertTrue(over.getMessage().contains("has 0 left to reserve"), over.getMessage());

        tx.executeWithoutResult(s -> orders.cancelItem(o, "00001", BigDecimal.ONE, "customer asked", "tester"));
        assertEquals(0, BigDecimal.ONE.compareTo(reservations.reservedQuantity(o, "00001")), "the reservation shrinks to what is left");
        assertEquals(0, before.subtract(BigDecimal.ONE).compareTo(inventory.atp(boot, dc)));
        // 1 x 100 + 15 + 7.50 - 10
        assertEquals(0, new BigDecimal("112.50").compareTo(total(o)));
        assertEquals("ITEM_APPROVED", status(o, "00001"));

        tx.executeWithoutResult(s -> orders.changeStatus(o, "ORDER_CANCELLED", "fraud", "tester"));
        assertEquals("ITEM_CANCELLED", status(o, "00001"));
        assertEquals("ITEM_CANCELLED", status(o, "00002"));
        assertEquals(0, before.compareTo(inventory.atp(boot, dc)), "all stock given back");
        assertEquals(0, new BigDecimal("7.50").compareTo(total(o)), "only order-level adjustments remain");
    }

    @Test
    void cancellingEveryItemCancelsTheOrderAndTheFlowIsEnforced() {
        String o = order();
        tx.executeWithoutResult(s -> {
            orders.cancelItem(o, "00001", null, null, "tester");
            orders.cancelItem(o, "00002", null, null, "tester");
        });
        assertEquals("ORDER_CANCELLED", status(o, null));
        assertThrows(StatusService.StatusTransitionException.class,
                () -> tx.executeWithoutResult(s -> orders.changeStatus(o, "ORDER_APPROVED", null, "tester")));
        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(s -> reservations.reserve(o, "00002", dc, null, "tester")));
    }

    @Test
    void anOrderCopiedInAStatusStartsThereAndRejectsBadInput() {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, null, "USD", null, null, null, null, null, null, null,
                List.of(new OrderService.NewItem(null, "Gift wrap", BigDecimal.ONE, new BigDecimal("4"), null, null, 0)),
                null, null, null, null, "ORDER_COMPLETED", "copied", null)));
        assertEquals("ORDER_COMPLETED", status(o, null));
        assertEquals("ITEM_COMPLETED", status(o, "00001"));
        assertEquals("copied", jdbc.queryForObject("SELECT change_reason FROM order_status WHERE order_id = ? AND order_item_seq_id IS NULL", String.class, o));

        IllegalArgumentException noItems = assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> orders.create(
                new OrderService.NewOrder(null, null, null, null, null, null, null, null, null, null, null, List.of(), null, null, null, null, null, null, null))));
        assertEquals("An order needs at least one item", noItems.getMessage());
        IllegalArgumentException badGroup = assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> orders.create(
                new OrderService.NewOrder(null, null, null, null, null, null, null, null, null, null, null,
                        List.of(new OrderService.NewItem(null, "x", BigDecimal.ONE, null, null, null, 2)), null, null, null, null, null, null, null))));
        assertTrue(badGroup.getMessage().contains("ship group 2"));
    }
}
