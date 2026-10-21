package com.nexus.core;

import com.nexus.core.config.CoreSchemaMigrator;
import com.nexus.core.service.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole application on real PostgreSQL: legacy migrations, then core migrations, then Hibernate validating every
 * entity (legacy and core) against the database, as production starts.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
class CoreOnPostgresAppTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")).withDatabaseName("nexus_oms");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.sql.init.mode", () -> "never");
        r.add("nexus.kafka.enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired StatusService statuses;
    @Autowired com.nexus.core.service.ProductService products;
    @Autowired com.nexus.core.service.PartyService parties;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @Autowired com.nexus.core.service.FacilityService facilities;
    @Autowired com.nexus.core.service.InventoryService inventory;
    @Autowired com.nexus.core.service.OrderService orders;
    @Autowired com.nexus.core.service.ShipmentService shipments;
    @Autowired com.nexus.core.service.ReturnService returns;
    @Autowired com.nexus.core.service.RoutingService routing;
    @Autowired com.nexus.core.service.ProductStoreService stores;

    @Test
    void startsWithBothSchemasAndTheSeededOrderFlow() {
        assertEquals(21, jdbc.queryForObject("SELECT COUNT(*) FROM " + CoreSchemaMigrator.HISTORY_TABLE + " WHERE success AND version IS NOT NULL AND version <> '0'", Long.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class) >= 75, "legacy migrations ran too");
        assertEquals("ORDER_CREATED", statuses.initialStatus(null, "ORDER_STATUS"));
        assertTrue(statuses.isAllowed(null, "ORDER_HOLD", "ORDER_APPROVED"));
        StatusService.StatusTransitionException e = assertThrows(StatusService.StatusTransitionException.class,
                () -> statuses.requireAllowed(null, "ORDER_COMPLETED", "ORDER_CREATED"));
        assertEquals("Can't change from Completed to Created; nothing can follow Completed.", e.getMessage());
    }

    /** Real PostgreSQL binds nulls and types stricter than H2: write through every kind of core column once. */
    @Test
    void writesProductsAndPartiesOnRealPostgres() {
        String product = tx.execute(s -> {
            String id = products.create(new com.nexus.core.service.ProductService.NewProduct(null, "Boot", null, null, null, null, null, false, false));
            products.setIdentification(id, "SKU", "PG-BOOT-1");
            products.setPrice(id, "DEFAULT_PRICE", "USD", null, new java.math.BigDecimal("10.5"));
            products.addToCategory(id, products.category("Footwear"), true);
            return id;
        });
        assertEquals(java.util.Optional.of(product), products.findByIdentification("SKU", "PG-BOOT-1"));
        String party = tx.execute(s -> {
            String id = parties.createPerson(com.nexus.core.service.PartyService.PersonName.parse("Pat Lee"), null);
            parties.addEmail(id, "pat@example.com", "PRIMARY_EMAIL");
            parties.addPostalAddress(id, new com.nexus.core.service.PartyService.AddressInput(null, "1 Main St", null, "Austin", "TX",
                    "78701", "US", null, null, null), "SHIPPING_LOCATION");
            return id;
        });
        assertEquals(java.util.List.of(party), parties.findByEmail("PAT@example.com"));
        assertEquals("US-TX", jdbc.queryForObject("SELECT state_province_geo_id FROM postal_address pa JOIN party_contact_mech p "
                + "ON p.contact_mech_id = pa.contact_mech_id WHERE p.party_id = ?", String.class, party));
    }

    /** An order's whole life on real PostgreSQL: stock, order, hold, ship in a box, return, refund. */
    @Test
    void runsAnOrderThroughShippingAndReturnOnRealPostgres() {
        java.math.BigDecimal two = new java.math.BigDecimal("2");
        String[] ids = tx.execute(s -> {
            String p = products.create(new com.nexus.core.service.ProductService.NewProduct(null, "PG Tee", null, null, null, null, null, false, false));
            String f = facilities.create(new com.nexus.core.service.FacilityService.NewFacility(null, "WAREHOUSE", "PG WH", null, null, null, null, null, null, null, null, 100L));
            inventory.receive(p, f, new java.math.BigDecimal("5"), new java.math.BigDecimal("3.25"), "USD", null, null);
            return new String[]{p, f};
        });
        String order = tx.execute(s -> orders.create(new com.nexus.core.service.OrderService.NewOrder(null, null, "WEB_SALES_CHANNEL", "USD", null, "PG-1", null,
                null, null, null, java.util.List.of(new com.nexus.core.service.OrderService.NewShipGroup("STANDARD", ids[1], null, null, null, true, false, null, null, null, null)),
                java.util.List.of(new com.nexus.core.service.OrderService.NewItem(ids[0], "Tee", two, new java.math.BigDecimal("12"), null, null, 0)),
                java.util.List.of(new com.nexus.core.service.OrderService.NewAdjustment("SALES_TAX", new java.math.BigDecimal("1.92"), null, null)),
                java.util.List.of(new com.nexus.core.service.OrderService.NewPayment("CREDIT_CARD", new java.math.BigDecimal("25.92"), "PAYMENT_SETTLED", null)),
                java.util.Map.of("SHOPIFY_ORD_ID", "PG-777"), null, null, null, "pg")));
        tx.executeWithoutResult(s -> {
            orders.changeStatus(order, "ORDER_APPROVED", null, "pg");
            orders.reserve(order, "00001", ids[1], null, "pg");
            assertTrue(facilities.takeOrder(ids[1], java.time.LocalDate.now()));
        });
        String sh = tx.execute(s -> shipments.confirm(new com.nexus.core.service.ShipmentService.Confirmation("PG-SH", order, "00001", null, "UPS", "STANDARD",
                java.util.List.of(new com.nexus.core.service.ShipmentService.ConfirmedPackage("1ZPG", new java.math.BigDecimal("0.4"), null, "BOX_SMALL",
                        java.util.Map.of("00001", two)))), "pg"));
        assertEquals("ORDER_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, order));
        assertEquals(0, new java.math.BigDecimal("3").compareTo(inventory.onHand(ids[0], ids[1])));
        String r = tx.execute(s -> returns.create(order, java.util.List.of(new com.nexus.core.service.ReturnService.NewReturnItem("00001", two, "RTN_NOT_WANT", null)), ids[1], null, "pg"));
        tx.executeWithoutResult(s -> {
            returns.accept(r, "pg");
            returns.receive(r, "00001", two, null, null, com.nexus.core.service.ReturnService.Grade.RESELLABLE, "pg");
        });
        tx.executeWithoutResult(s -> returns.complete(r, "re_pg", "pg"));
        assertEquals(0, new java.math.BigDecimal("24").compareTo(returns.refundAmount(r)));
        assertEquals(0, new java.math.BigDecimal("5").compareTo(inventory.onHand(ids[0], ids[1])));
        assertEquals("SHIPMENT_SHIPPED", shipments.status(sh));
    }

    /** A routing run and a simulation on real PostgreSQL (collection parameters, row locks, the rollback). */
    @Test
    void routesOnRealPostgres() {
        String p = tx.execute(s -> products.create(new com.nexus.core.service.ProductService.NewProduct(null, "PG Cap", null, null, null, null, null, false, false)));
        String f = tx.execute(s -> facilities.create(new com.nexus.core.service.FacilityService.NewFacility(null, "RETAIL_STORE", "PG Store", null, null, null,
                new java.math.BigDecimal("40.7"), new java.math.BigDecimal("-74.0"), null, null, null, 10L)));
        tx.executeWithoutResult(s -> {
            inventory.receive(p, f, new java.math.BigDecimal("3"), null, null, null, null);
            stores.createIfMissing(new com.nexus.core.service.ProductStoreService.NewStore("PG-STORE", "PG", "USD", null, "WEB_SALES_CHANNEL", null, null));
        });
        String order = tx.execute(s -> orders.create(new com.nexus.core.service.OrderService.NewOrder(null, "PG-STORE", "WEB_SALES_CHANNEL", "USD", null, null, null,
                null, null, null, null, java.util.List.of(new com.nexus.core.service.OrderService.NewItem(p, "Cap", java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, null, null, 0)),
                null, null, null, null, null, null, "pg")));
        tx.executeWithoutResult(s -> orders.changeStatus(order, "ORDER_APPROVED", null, "pg"));
        String g = routing.createGroup("PG-STORE", "PG", null);
        String r = routing.addRouting(g, "Queue", 1, java.util.List.of(com.nexus.core.service.RoutingService.Cond.filter("facilityId", "in", "_NA_")));
        routing.addRule(r, "Nearest", 1, true, java.util.List.of(com.nexus.core.service.RoutingService.Cond.sortBy("distance", "asc"),
                com.nexus.core.service.RoutingService.Cond.sortBy("brokenSizeRun", "asc")), java.util.Map.of("ORA_MV_TO_QUEUE", "UNFILLABLE_PARKING"));
        routing.activate(r);
        assertEquals(1, routing.simulate(g, "pg").routed());
        org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, order));
        assertEquals(1, routing.run(g, "pg").routed());
        assertEquals(f, jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, order));
    }
}
