package com.nexus.core;

import com.nexus.core.config.CoreSchemaMigrator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core migrations build an empty database from scratch, on PostgreSQL and on MySQL, with the same rows, and
 * running them again changes nothing. A database that already holds legacy tables gets the core tables beside them.
 */
@Testcontainers(disabledWithoutDocker = true)
class CoreSchemaMigrationTest {

    static final int TABLES = 165;

    @Test
    void buildsOnPostgres() {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            assertBuildsAndSeeds(pg, "postgresql");
        }
    }

    @Test
    void buildsBesideLegacyTablesOnPostgres() {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            JdbcTemplate jdbc = new JdbcTemplate(ds(pg));
            jdbc.execute("CREATE TABLE nx_orders (id VARCHAR(40) PRIMARY KEY)");
            CoreSchemaMigrator.migrate(ds(pg));
            assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'nx_orders'"));
            assertTrue(count(jdbc, "SELECT COUNT(*) FROM status_item") > 0);
        }
    }

    /** Needs the mysql:8.4 image (about 600 MB). Run with -Dnexus.test.mysql=true. */
    @Test
    @EnabledIfSystemProperty(named = "nexus.test.mysql", matches = "true")
    void buildsOnMysql() {
        try (MySQLContainer<?> my = new MySQLContainer<>("mysql:8.4")) {
            my.start();
            assertBuildsAndSeeds(my, "mysql");
        }
    }

    /** Without Docker MySQL: the MySQL scripts must at least parse and seed on H2 in MySQL mode. */
    @Test
    void mysqlScriptsRunOnH2InMysqlMode() {
        DriverManagerDataSource h2 = new DriverManagerDataSource("jdbc:h2:mem:core_mysql;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(h2).table(CoreSchemaMigrator.HISTORY_TABLE)
                .locations("classpath:db/core/common", "classpath:db/core/mysql").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(h2);
        assertSeeded(jdbc);
    }

    private static void assertBuildsAndSeeds(JdbcDatabaseContainer<?> db, String vendor) {
        DataSource ds = ds(db);
        assertEquals(vendor, CoreSchemaMigrator.vendor(ds));
        assertEquals(25, CoreSchemaMigrator.migrate(ds).migrationsExecuted);
        assertEquals(0, CoreSchemaMigrator.migrate(ds).migrationsExecuted, "a second run changes nothing");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String schemaFilter = vendor.equals("mysql") ? "table_schema = DATABASE()" : "table_schema = 'public'";
        assertEquals(TABLES, count(jdbc, "SELECT COUNT(*) FROM information_schema.tables WHERE " + schemaFilter
                + " AND table_name <> '" + CoreSchemaMigrator.HISTORY_TABLE + "'"));
        assertSeeded(jdbc);
        // foreign keys are enforced: a status of an unknown type is refused
        try {
            jdbc.update("INSERT INTO status_item (status_id, status_type_id) VALUES ('X', 'NO_SUCH_TYPE')");
            throw new AssertionError("foreign key not enforced");
        } catch (org.springframework.dao.DataIntegrityViolationException expected) {
            // good
        }
    }

    private static void assertSeeded(JdbcTemplate jdbc) {
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM status_flow_transition WHERE status_id = 'ORDER_CREATED' AND to_status_id = 'ORDER_APPROVED'"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM status_flow_item WHERE status_id = 'ORDER_CREATED' AND is_initial = 'Y'"));
        assertEquals(52, count(jdbc, "SELECT COUNT(*) FROM geo_assoc WHERE geo_id = 'USA'"));
        assertEquals(36, count(jdbc, "SELECT COUNT(*) FROM geo_assoc WHERE geo_id = 'IND'"));
        assertEquals("Maharashtra", jdbc.queryForObject("SELECT geo_name FROM geo WHERE geo_id = 'IN-MH'", String.class));
        assertEquals("₹", jdbc.queryForObject("SELECT abbreviation FROM uom WHERE uom_id = 'INR'", String.class));
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static DataSource ds(JdbcDatabaseContainer<?> db) {
        return new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }
}
