package com.nexus.oms.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tenant isolation on real Postgres 16: all Flyway migrations, Row-Level Security,
 * and the app connecting as a superuser (as docker-compose does), so isolation must come from
 * the per-request SET ROLE nexus_app, not from luck in the queries. H2 cannot test any of this.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationPostgresTest {

    // Started eagerly: with a PER_CLASS lifecycle Spring builds the context when the test
    // instance is created, before the @Container extension would start the database.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("nexus_oms");

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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.sql.init.mode", () -> "never");
        r.add("nexus.kafka.enabled", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManagerFactory emf;

    private String tokenA;
    private String tokenB;

    @BeforeAll
    void tenants() throws Exception {
        tokenA = register("tenant-a-" + System.nanoTime());
        tokenB = register("tenant-b-" + System.nanoTime());
    }

    @Test
    void migrationsRanAndRlsIsForcedOnEveryTenantTable() {
        Integer unprotected = jdbc.queryForObject("""
                SELECT count(*) FROM pg_class c
                JOIN information_schema.columns col
                  ON col.table_name = c.relname AND col.column_name = 'tenant_id' AND col.table_schema = 'public'
                WHERE c.relkind = 'r' AND NOT (c.relrowsecurity AND c.relforcerowsecurity)""", Integer.class);
        assertEquals(0, unprotected, "every tenant_id table must have FORCE ROW LEVEL SECURITY");
    }

    /**
     * Every NOT NULL column without a default must be written by its entity. A required column
     * that no entity maps makes every insert fail (pickers.user_id, edi_documents.document_type
     * were both found this way). Uses Hibernate's own metamodel, so it sees what JPA writes.
     */
    @Test
    void everyRequiredColumnIsMappedByItsEntity() {
        java.util.Map<String, java.util.Set<String>> mapped = new java.util.HashMap<>();
        emf.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getMappingMetamodel()
                .forEachEntityDescriptor(entity -> {
                    if (!(entity instanceof org.hibernate.persister.entity.AbstractEntityPersister p)) return;
                    String table = p.getTableName().toLowerCase();
                    java.util.Set<String> columns = mapped.computeIfAbsent(table, t -> new java.util.HashSet<>());
                    entity.forEachSelectable((i, sel) -> columns.add(sel.getSelectionExpression().toLowerCase()));
                    for (String id : p.getIdentifierColumnNames()) columns.add(id.toLowerCase());
                });

        java.util.List<String> unmapped = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND is_nullable = 'NO' AND column_default IS NULL""", String.class)
                .stream()
                .filter(tc -> {
                    String[] parts = tc.split("\\.");
                    java.util.Set<String> cols = mapped.get(parts[0]);
                    return cols != null && !cols.contains(parts[1]);
                })
                .toList();
        assertTrue(mapped.size() > 100, "metamodel should list the entities, got " + mapped.size());
        assertTrue(unmapped.isEmpty(), "required columns no entity writes (inserts will fail): " + unmapped);
    }

    @Test
    void tenantBCannotReadChangeOrDeleteTenantACustomer() throws Exception {
        String customerA = data(call(HttpMethod.POST, "/customers", tokenA,
                "{\"name\":\"Alice Co\",\"email\":\"alice-" + UUID.randomUUID() + "@a.test\"}")).get("id").asText();

        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/customers/" + customerA, tokenB, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.PUT, "/customers/" + customerA, tokenB,
                "{\"name\":\"pwned\"}").getStatusCode());
        call(HttpMethod.DELETE, "/customers/" + customerA, tokenB, null);
        assertFalse(call(HttpMethod.GET, "/customers", tokenB, null).getBody().contains(customerA),
                "tenant B's list must not contain tenant A's customer");

        ResponseEntity<String> stillThere = call(HttpMethod.GET, "/customers/" + customerA, tokenA, null);
        assertEquals(HttpStatus.OK, stillThere.getStatusCode(), "B's delete must not touch A's row");
        assertEquals("Alice Co", data(stillThere).get("name").asText());
    }

    @Test
    void tenantBCannotSeeOrCancelTenantAOrder() throws Exception {
        String orderA = data(call(HttpMethod.POST, "/orders", tokenA, """
                {"channel":"WEB","customerName":"Alice","customerEmail":"alice@a.test","currency":"USD",
                 "shippingAddress":{"line1":"1 Main St","city":"Newark","state":"NJ","pincode":"07105","country":"US"},
                 "items":[{"sku":"SKU-1","productName":"Widget","quantity":1,"unitPrice":10.00}]}""")).get("id").asText();

        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/orders/" + orderA, tokenB, null).getStatusCode());
        assertNotEquals(HttpStatus.OK, call(HttpMethod.POST, "/orders/" + orderA + "/cancel", tokenB, null).getStatusCode());
        assertEquals("PENDING", data(call(HttpMethod.GET, "/orders/" + orderA, tokenA, null)).get("status").asText());
    }

    // ------------------------------------------------------------------ helpers

    /** What H2 cannot prove: the guards on inventory publishing are enforced by PostgreSQL itself. */
    @Test
    void inventoryPublishingGuardsAreEnforcedByTheDatabase() {
        UUID tenant = UUID.randomUUID(), store = UUID.randomUUID(), nodeA = UUID.randomUUID(), nodeB = UUID.randomUUID();
        com.nexus.oms.security.TenantContext.setCurrentTenantId(tenant);   // row-level security applies to this connection
        try {
            inventoryPublishingGuards(tenant, store, nodeA, nodeB);
        } finally {
            com.nexus.oms.security.TenantContext.clear();
        }
    }

    private void inventoryPublishingGuards(UUID tenant, UUID store, UUID nodeA, UUID nodeB) {
        String insertMapping = "INSERT INTO nx_channel_location_mappings (id, tenant_id, integration_store_id, node_id, external_location_id) VALUES (?,?,?,?,?)";

        // A node feeds one channel location per store: offering its stock at two would count it twice.
        jdbc.update(insertMapping, UUID.randomUUID(), tenant, store, nodeA, "111");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update(insertMapping, UUID.randomUUID(), tenant, store, nodeA, "222"));
        // Several nodes may share one channel location (a pooled location).
        jdbc.update(insertMapping, UUID.randomUUID(), tenant, store, nodeB, "111");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO nx_channel_location_mappings (id, tenant_id, integration_store_id, node_id, external_location_id, buffer_units) VALUES (?,?,?,?,?,-1)",
                        UUID.randomUUID(), tenant, store, UUID.randomUUID(), "333"));

        // At most one run in flight per store, but any number of finished ones.
        String insertRun = "INSERT INTO nx_inventory_publish_runs (id, tenant_id, integration_store_id, status) VALUES (?,?,?,?)";
        UUID running = UUID.randomUUID();
        jdbc.update(insertRun, running, tenant, store, "RUNNING");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update(insertRun, UUID.randomUUID(), tenant, store, "RUNNING"));
        jdbc.update(insertRun, UUID.randomUUID(), tenant, store, "COMPLETED");
        jdbc.update(insertRun, UUID.randomUUID(), tenant, UUID.randomUUID(), "RUNNING");   // another store is independent
        jdbc.update("UPDATE nx_inventory_publish_runs SET status = 'COMPLETED' WHERE id = ?", running);
        jdbc.update(insertRun, UUID.randomUUID(), tenant, store, "RUNNING");               // the slot is free again

        // Deleting a run removes its report lines.
        jdbc.update("INSERT INTO nx_inventory_publish_lines (id, tenant_id, run_id, sku, external_location_id, calculated_qty, outcome) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenant, running, "SKU-1", "111", 5, "PUBLISHED");
        jdbc.update("DELETE FROM nx_inventory_publish_runs WHERE id = ?", running);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM nx_inventory_publish_lines WHERE run_id = ?", Integer.class, running));
    }

    /** The ledger is append-only: the database refuses to edit or delete a line, whoever asks. */
    @Test
    void inventoryLedgerCannotBeEditedOrDeleted() {
        UUID tenant = UUID.randomUUID(), id = UUID.randomUUID();
        com.nexus.oms.security.TenantContext.setCurrentTenantId(tenant);
        try {
            jdbc.update("INSERT INTO nx_inventory_movements (id, tenant_id, sku, movement_type, on_hand_delta) VALUES (?,?,?,?,?)",
                    id, tenant, "SKU-L", "RECEIPT", 5);
            Exception update = assertThrows(Exception.class, () -> jdbc.update("UPDATE nx_inventory_movements SET on_hand_delta = 999 WHERE id = ?", id));
            assertTrue(String.valueOf(update.getMessage()).contains("append-only"), update.getMessage());
            Exception delete = assertThrows(Exception.class, () -> jdbc.update("DELETE FROM nx_inventory_movements WHERE id = ?", id));
            assertTrue(String.valueOf(delete.getMessage()).contains("append-only"), delete.getMessage());
            assertEquals(5, jdbc.queryForObject("SELECT on_hand_delta FROM nx_inventory_movements WHERE id = ?", Integer.class, id));
        } finally {
            com.nexus.oms.security.TenantContext.clear();
        }
    }

    private String register(String username) throws Exception {
        ResponseEntity<String> r = call(HttpMethod.POST, "/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"Test1234!\",\"role\":\"ADMIN\"}");
        assertEquals(HttpStatus.OK, r.getStatusCode(), r.getBody());
        return data(r).get("accessToken").asText();
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange("http://localhost:" + port + "/api/v1" + path, method, new HttpEntity<>(body, h), String.class);
    }

    private JsonNode data(ResponseEntity<String> r) throws Exception {
        assertTrue(r.getStatusCode().is2xxSuccessful(), r.getStatusCode() + " " + r.getBody());
        return mapper.readTree(r.getBody()).get("data");
    }
}
