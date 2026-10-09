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
