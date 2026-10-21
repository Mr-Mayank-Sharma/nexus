package com.nexus.oms.coremigration;

import com.nexus.core.CoreTestData;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.ProductService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacilityInventoryBackfillTest {

    @Autowired FacilityInventoryBackfill backfill;
    @Autowired ProductBackfill productBackfill;
    @Autowired FacilityService facilities;
    @Autowired InventoryService inventory;
    @Autowired ProductService products;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    final UUID tenant = UUID.randomUUID();
    final UUID store = UUID.randomUUID();
    final UUID dc = UUID.randomUUID();
    final UUID warehouse = UUID.randomUUID();

    @BeforeAll
    void legacy() {
        CoreTestData.ensureSeeded(jdbc);
        jdbc.update("INSERT INTO nx_company_settings (id, tenant_id, company_name, default_currency, created_at, updated_at) VALUES (?, ?, 'Stock Co', 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO products (id, tenant_id, sku, product_name, unit_price, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'STK-TEE', 'Tee', 20.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO products (id, tenant_id, sku, product_name, unit_price, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'STK-MILK', 'Milk', 2.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO nx_nodes (id, tenant_id, name, type, is_active, capacity_daily) VALUES (?, ?, 'High Street', 'STORE', TRUE, 40)", store, tenant);
        jdbc.update("INSERT INTO nx_nodes (id, tenant_id, name, type, is_active) VALUES (?, ?, 'Central DC', 'DC', TRUE)", dc, tenant);
        jdbc.update("INSERT INTO nx_warehouses (id, tenant_id, code, name, type, zip_code, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'wh-77', 'Cold store', 'COLD_STORE', '07030', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", warehouse, tenant);
        stock("STK-MILK", warehouse, 8, 0, 0, 0, null, null, null);
        stock("STK-TEE", store, 10, 2, 1, 0, 3, null, null);
        stock("STK-TEE", dc, 100, 0, 0, 4, 10, null, null);
        stock("STK-MILK", dc, 30, 0, 0, 0, null, "L-2026/10", LocalDate.of(2026, 11, 1));
        stock("STK-MILK", dc, 12, 0, 0, 0, null, "L-2026/11", LocalDate.of(2026, 12, 1));
        stock("STK-NOPE", dc, 5, 0, 0, 0, null, null, null);   // no such product
        stock("STK-TEE", null, 7, 0, 0, 0, null, null, null);  // no location
    }

    private void stock(String sku, UUID node, int onHand, int allocated, int reserved, int damaged, Integer safety, String lot, LocalDate expiry) {
        jdbc.update("INSERT INTO nx_inventory (id, version, tenant_id, sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, "
                        + "quantity_damaged, safety_stock, lot_number, expiry_date, created_at, updated_at) VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), tenant, sku, node, onHand, allocated, reserved, damaged, safety, lot, expiry);
    }

    @Test
    void copiesLocationsAndStockOnce() {
        productBackfill.run(tenant);
        FacilityInventoryBackfill.Result first = backfill.run(tenant);
        assertEquals(3, first.facilitiesCreated());
        assertEquals(5, first.itemsCreated());
        assertEquals(1, first.rowsWithUnknownSku());
        assertEquals(1, first.rowsWithoutLocation());

        String high = facilities.findByIdentification("NEXUS_LEGACY_ID", store.toString()).orElseThrow();
        String central = facilities.findByIdentification("NEXUS_LEGACY_ID", dc.toString()).orElseThrow();
        assertEquals("RETAIL_STORE", jdbc.queryForObject("SELECT facility_type_id FROM facility WHERE facility_id = ?", String.class, high));
        assertEquals("DISTRIBUTION_CENTER", jdbc.queryForObject("SELECT facility_type_id FROM facility WHERE facility_id = ?", String.class, central));
        assertEquals(Optional.of("WH-77"), facilities.findByIdentification("NEXUS_LEGACY_ID", warehouse.toString()), "a warehouse keeps its code as id");
        assertEquals("07030", jdbc.queryForObject("SELECT postal_code FROM facility WHERE facility_id = 'WH-77'", String.class));
        assertEquals(40L, jdbc.queryForObject("SELECT maximum_order_limit FROM facility WHERE facility_id = ?", Long.class, high));

        String tee = products.findByIdentification("SKU", "STK-TEE").orElseThrow();
        String milk = products.findByIdentification("SKU", "STK-MILK").orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(inventory.onHand(tee, high)));
        assertEquals(0, new BigDecimal("7").compareTo(inventory.atp(tee, high)), "allocated and reserved stay held");
        assertEquals(0, new BigDecimal("42").compareTo(inventory.onHand(milk, central)), "two lots");
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_item WHERE product_id = ? AND lot_id IS NOT NULL", Long.class, milk));
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(), em.find(com.nexus.core.model.inventory.Lot.class, "L-2026_11").getExpirationDate(),
                "read through JPA: tests run outside main(), which sets the JVM to UTC");
        assertEquals(0, new BigDecimal("3").compareTo(jdbc.queryForObject("SELECT minimum_stock FROM product_facility WHERE product_id = ? AND facility_id = ?", BigDecimal.class, tee, high)));
        assertEquals(0, new BigDecimal("4").compareTo(jdbc.queryForObject("SELECT computed_last_inventory_count FROM product_facility WHERE product_id = ? AND facility_id = ?", BigDecimal.class, tee, high)));
        assertEquals("4 damaged in the earlier model", jdbc.queryForObject("SELECT comments FROM inventory_item WHERE product_id = ? AND facility_id = ?", String.class, tee, central));
        assertEquals(5L, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_item_detail d JOIN inventory_item i ON i.inventory_item_id = d.inventory_item_id "
                + "WHERE i.product_id IN (?, ?) AND d.reason_enum_id = 'IID_MIGRATION'", Long.class, tee, milk));

        FacilityInventoryBackfill.Result second = backfill.run(tenant);
        assertEquals(0, second.facilitiesCreated());
        assertEquals(0, second.itemsCreated());
        assertEquals(5, second.itemsAlreadyCopied());
    }

    @Test
    void mapsNodeTypesAndLotNumbers() {
        assertEquals("THIRD_PARTY_LOGISTICS", FacilityInventoryBackfill.facilityType("3pl"));
        assertEquals("WAREHOUSE", FacilityInventoryBackfill.facilityType("anything"));
        assertEquals("A_1", FacilityInventoryBackfill.lotId(" A 1 "));
        assertNull(FacilityInventoryBackfill.lotId("  "));
    }
}
