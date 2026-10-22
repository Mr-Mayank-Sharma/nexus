package com.nexus.oms.coremigration;

import com.nexus.core.CoreTestData;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ProductService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderBackfillTest {

    @Autowired OrderBackfill backfill;
    @Autowired CustomerPartyBackfill customerBackfill;
    @Autowired ProductBackfill productBackfill;
    @Autowired FacilityInventoryBackfill stockBackfill;
    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired ProductService products;
    @Autowired FacilityService facilities;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;

    final UUID tenant = UUID.randomUUID();
    final UUID customer = UUID.randomUUID();
    final UUID warehouse = UUID.randomUUID();
    final UUID shopifyOrder = UUID.randomUUID();
    final UUID guestOrder = UUID.randomUUID();
    final UUID shippedOrder = UUID.randomUUID();

    @BeforeAll
    void legacy() {
        CoreTestData.ensureSeeded(jdbc);
        UUID addr = UUID.randomUUID();
        UUID guestAddr = UUID.randomUUID();
        jdbc.update("INSERT INTO nx_company_settings (id, tenant_id, company_name, default_currency, created_at, updated_at) VALUES (?, ?, 'Order Co', 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO nx_addresses (id, tenant_id, address_line1, city, state, postal_code, country, full_name, created_at, updated_at) "
                + "VALUES (?, ?, '5 River St', 'Hoboken', 'NJ', '07030', 'US', 'Mia Chen', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", addr, tenant);
        jdbc.update("INSERT INTO nx_addresses (id, tenant_id, address_line1, city, state, postal_code, country, full_name, created_at, updated_at) "
                + "VALUES (?, ?, '9 Pier Rd', 'Jersey City', 'NJ', '07302', 'US', 'Walk In', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", guestAddr, tenant);
        jdbc.update("INSERT INTO nx_customers (id, tenant_id, name, email, address_id, created_at) VALUES (?, ?, 'Mia Chen', 'mia@example.com', ?, CURRENT_TIMESTAMP)", customer, tenant, addr);
        jdbc.update("INSERT INTO nx_integration_stores (id, tenant_id, store_code, store_name, platform, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'ord-shop', 'Order shop', 'SHOPIFY', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO products (id, tenant_id, sku, product_name, unit_price, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'ORD-JKT', 'Jacket', 80.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO nx_warehouses (id, tenant_id, code, name, type, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'ord-wh', 'Order WH', 'WAREHOUSE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", warehouse, tenant);
        // 10 on hand, 2 allocated to the Shopify order below
        jdbc.update("INSERT INTO nx_inventory (id, version, tenant_id, sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, created_at, updated_at) "
                + "VALUES (?, 0, ?, 'ORD-JKT', ?, 10, 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant, warehouse);

        // 2 x 80 = 160, + 5 shipping + 3 tax, total 164: a 4.00 discount the old model kept only in the total
        order(shopifyOrder, "PENDING", "SHOPIFY", "450789469", customer, null, "160", "5", "3", "164", "paid", "STANDARD");
        jdbc.update("INSERT INTO nx_order_items (id, order_id, sku, product_name, quantity, unit_price, total_price, allocated_node_id, allocated_qty, fulfilled_qty, consumed_qty) "
                + "VALUES (?, ?, 'ORD-JKT', 'Jacket', 2, 80.00, 160.00, ?, 2, 0, 0)", UUID.randomUUID(), shopifyOrder, warehouse);
        order(guestOrder, "CANCELLED", "POS", null, null, guestAddr, "12", "0", "0", "12", null, "BOPIS");
        jdbc.update("INSERT INTO nx_order_items (id, order_id, sku, product_name, quantity, unit_price, total_price, consumed_qty) "
                + "VALUES (?, ?, 'NOT-A-SKU', 'Mystery', 1, 12.00, 12.00, 0)", UUID.randomUUID(), guestOrder);
        order(shippedOrder, "SHIPPED", "AMAZON", "111-222", customer, null, "0", "0", "0", "0", "paid", null);
    }

    private void order(UUID id, String status, String channel, String channelOrderId, UUID customerId, UUID shipTo, String sub,
                       String ship, String tax, String total, String payment, String fulfillment) {
        jdbc.update("INSERT INTO nx_orders (id, version, tenant_id, external_id, channel, channel_order_id, customer_id, status, fulfillment_type, "
                        + "subtotal, shipping_cost, tax_amount, total, payment_status, ship_to_address_id, order_type, created_at, updated_at) "
                        + "VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'B2C', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, tenant, "EXT-" + id.toString().substring(0, 6), channel, channelOrderId, customerId, status, fulfillment,
                new BigDecimal(sub), new BigDecimal(ship), new BigDecimal(tax), new BigDecimal(total), payment, shipTo);
    }

    @Test
    void copiesOrdersWithTheirMoneyStatusAndHolds() {
        customerBackfill.run(tenant);
        productBackfill.run(tenant);
        stockBackfill.run(tenant);
        String jacket = products.findByIdentification("SKU", "ORD-JKT").orElseThrow();
        String wh = facilities.findByIdentification("NEXUS_LEGACY_ID", warehouse.toString()).orElseThrow();
        BigDecimal atpBefore = inventory.atp(jacket, wh);
        assertEquals(0, new BigDecimal("8").compareTo(atpBefore));

        OrderBackfill.Result first = backfill.run(tenant);
        assertEquals(3, first.ordersCreated());
        assertEquals(1, first.itemsWithoutProduct());
        assertEquals(1, first.guestParties());
        assertEquals(1, first.reservations());
        assertEquals(1, first.totalsBalanced());

        String shop = orders.findByIdentification("NEXUS_LEGACY_ID", shopifyOrder.toString()).orElseThrow();
        assertEquals(shop, orders.findByIdentification("SHOPIFY_ORD_ID", "450789469").orElseThrow());
        assertEquals(0, new BigDecimal("164").compareTo(jdbc.queryForObject("SELECT grand_total FROM order_header WHERE order_id = ?", BigDecimal.class, shop)));
        assertEquals(0, new BigDecimal("-4").compareTo(jdbc.queryForObject("SELECT amount FROM order_adjustment WHERE order_id = ? AND order_adjustment_type_id = 'DISCOUNT_ADJUSTMENT'", BigDecimal.class, shop)));
        assertEquals("ORDER_CREATED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, shop));
        assertEquals("WEB_SALES_CHANNEL", jdbc.queryForObject("SELECT sales_channel_enum_id FROM order_header WHERE order_id = ?", String.class, shop));
        assertEquals("ORD-SHOP", jdbc.queryForObject("SELECT product_store_id FROM order_header WHERE order_id = ?", String.class, shop));
        assertEquals("PAYMENT_SETTLED", jdbc.queryForObject("SELECT status_id FROM order_payment_preference WHERE order_id = ?", String.class, shop));
        assertEquals(wh, jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, shop));
        assertNotNull(jdbc.queryForObject("SELECT contact_mech_id FROM order_item_ship_group WHERE order_id = ?", String.class, shop), "the customer's address");
        assertEquals(0, new BigDecimal("2").compareTo(reservations.reservedQuantity(shop, "00001")));
        assertEquals(0, atpBefore.compareTo(inventory.atp(jacket, wh)), "the hold was already out of available; no stock moves");

        String guest = orders.findByIdentification("NEXUS_LEGACY_ID", guestOrder.toString()).orElseThrow();
        assertEquals("ORDER_CANCELLED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, guest));
        assertEquals("ITEM_CANCELLED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ?", String.class, guest));
        assertEquals("STOREPICKUP", jdbc.queryForObject("SELECT shipment_method_type_id FROM order_item_ship_group WHERE order_id = ?", String.class, guest));
        assertEquals("BOPIS", jdbc.queryForObject("SELECT attr_value FROM order_attribute WHERE order_id = ? AND attr_name = 'legacyFulfillmentType'", String.class, guest));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM order_role WHERE order_id = ? AND role_type_id = 'PLACING_CUSTOMER'", Long.class, guest), "a guest party");

        String shipped = orders.findByIdentification("NEXUS_LEGACY_ID", shippedOrder.toString()).orElseThrow();
        assertEquals("ORDER_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, shipped));
        assertEquals("SHIPPED", jdbc.queryForObject("SELECT attr_value FROM order_attribute WHERE order_id = ? AND attr_name = 'legacyStatus'", String.class, shipped));

        OrderBackfill.Result second = backfill.run(tenant);
        assertEquals(0, second.ordersCreated());
        assertEquals(3, second.ordersAlreadyCopied());
    }

    @Test
    void mapsLegacyValues() {
        assertEquals("ORDER_APPROVED", OrderBackfill.orderStatus("partially_allocated"));
        assertEquals("ORDER_HOLD", OrderBackfill.orderStatus("FAILED"));
        assertEquals("B2B_SALES_CHANNEL", OrderBackfill.salesChannel("EDI"));
        assertEquals("EDI_PO_NUMBER", OrderBackfill.channelIdType("B2B_PORTAL"));
        assertEquals("PAYMENT_AUTHORIZED", OrderBackfill.paymentStatus("authorized"));
        assertEquals("STANDARD", OrderBackfill.shipmentMethod("AI_BATCH"));
    }
}
