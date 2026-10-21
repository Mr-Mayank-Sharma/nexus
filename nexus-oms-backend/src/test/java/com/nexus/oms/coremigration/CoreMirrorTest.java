package com.nexus.oms.coremigration;

import com.nexus.core.CoreTestData;
import com.nexus.core.service.OrderService;
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
class CoreMirrorTest {

    @Autowired CoreMirror mirror;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;

    final UUID tenant = UUID.randomUUID();
    final UUID first = UUID.randomUUID();

    @BeforeAll
    void legacy() {
        CoreTestData.ensureSeeded(jdbc);
        jdbc.update("INSERT INTO nx_company_settings (id, tenant_id, company_name, default_currency, created_at, updated_at) VALUES (?, ?, 'Mirror Co', 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        order(first, "MIR-1");
    }

    private void order(UUID id, String external) {
        jdbc.update("INSERT INTO nx_orders (id, version, tenant_id, external_id, channel, status, subtotal, total, order_type, created_at, updated_at) "
                + "VALUES (?, 0, ?, ?, 'SHOPIFY', 'PENDING', 20, 20, 'B2C', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id, tenant, external);
        jdbc.update("INSERT INTO nx_order_items (id, order_id, sku, product_name, quantity, unit_price, total_price, consumed_qty) "
                + "VALUES (?, ?, 'MIR-SKU', 'Mug', 2, 10.00, 20.00, 0)", UUID.randomUUID(), id);
    }

    @Test
    void copiesNewOrdersThenFollowsTheirChanges() {
        mirror.runOnce();
        String a = orders.findByIdentification("NEXUS_LEGACY_ID", first.toString()).orElseThrow();
        assertEquals("ORDER_CREATED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, a));

        // a new order arrives and the first one ships in the earlier model
        UUID second = UUID.randomUUID();
        order(second, "MIR-2");
        jdbc.update("UPDATE nx_orders SET status = 'SHIPPED', tracking_number = '1Z999', updated_at = CURRENT_TIMESTAMP WHERE id = ?", first);

        CoreMirror.Run run = mirror.runOnce();
        assertEquals(0, run.failures());
        assertTrue(run.ordersCopied() >= 1);
        String b = orders.findByIdentification("NEXUS_LEGACY_ID", second.toString()).orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(jdbc.queryForObject("SELECT grand_total FROM order_header WHERE order_id = ?", BigDecimal.class, b)));
        assertEquals("ORDER_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, a));
        assertEquals("ITEM_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ?", String.class, a));
        assertEquals("1Z999", jdbc.queryForObject("SELECT tracking_number FROM order_item_ship_group WHERE order_id = ?", String.class, a));
        assertEquals("SHIPPED", jdbc.queryForObject("SELECT attr_value FROM order_attribute WHERE order_id = ? AND attr_name = 'legacyStatus'", String.class, a));
        assertEquals("Changed in the earlier model to SHIPPED", jdbc.queryForObject(
                "SELECT change_reason FROM order_status WHERE order_id = ? AND status_id = 'ORDER_COMPLETED'", String.class, a));

        jdbc.update("UPDATE nx_orders SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", second);
        mirror.runOnce();
        assertEquals("ORDER_CANCELLED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, b));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT grand_total FROM order_header WHERE order_id = ?", BigDecimal.class, b)));
    }
}
