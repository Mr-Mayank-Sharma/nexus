package com.nexus.oms.tenancy;

import com.nexus.core.CoreTransactional;
import com.nexus.core.config.CoreSchemaMigrator;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.SequenceService;
import com.nexus.core.tenancy.TenantScope;
import com.nexus.oms.NexusOmsApplication;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One core database per company (doc 29, phase B), on real PostgreSQL: a company given its own database writes and reads
 * only there; a company without one, and code with no one signed in, use the shared database.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = NexusOmsApplication.class)
@ActiveProfiles("test")
class TenantDatabasesPostgresTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")).withDatabaseName("nexus_oms");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("nexus.kafka.enabled", () -> "false");
    }

    @Autowired TenantDatabaseRegistry tenantDatabases;
    @Autowired TenantScope tenantScope;
    @Autowired ProductService productService;
    @Autowired SequenceService sequenceService;
    @Autowired JdbcTemplate sharedJdbc;
    @Autowired @Qualifier(CoreTransactional.JDBC) JdbcTemplate coreJdbc;

    @Test
    void aCompanyWithItsOwnDatabaseSeesOnlyItsOwnData() {
        UUID companyWithOwnDatabase = UUID.randomUUID();
        UUID companyOnSharedDatabase = UUID.randomUUID();
        String jdbcUrl = tenantDatabases.provision(companyWithOwnDatabase, POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        assertTrue(jdbcUrl.contains("/nexus_t_"), jdbcUrl);

        // Its database was migrated before first use.
        long coreMigrations = TenantContext.callAs(companyWithOwnDatabase, () -> coreJdbc.queryForObject(
                "SELECT COUNT(*) FROM " + CoreSchemaMigrator.HISTORY_TABLE + " WHERE success AND version <> '0'", Long.class));
        assertTrue(coreMigrations >= 23, "core schema migrated into the company's database: " + coreMigrations);

        TenantContext.callAs(companyWithOwnDatabase, () -> newProduct("Only in its own database"));
        String sharedProduct = TenantContext.callAs(companyOnSharedDatabase, () -> newProduct("In the shared database"));

        // The company with its own database finds its product, and not the other one. (Ids are counted per database, so
        // the two products may share an id; names tell them apart.)
        assertEquals(1, countProductNamed(companyWithOwnDatabase, "Only in its own database"));
        assertEquals(0, countProductNamed(companyWithOwnDatabase, "In the shared database"));
        // The shared database holds the other company's product, and never the first one's.
        assertEquals(0, sharedJdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name = 'Only in its own database'", Long.class));
        assertEquals(1, sharedJdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_id = ?", Long.class, sharedProduct));

        // Ids are counted per database, so the two can repeat without ever meeting.
        long ownNextId = TenantContext.callAs(companyWithOwnDatabase, () -> sequenceService.nextLong("IsolationProbe"));
        long sharedNextId = sequenceService.nextLong("IsolationProbe");
        assertEquals(10000, ownNextId);
        assertEquals(10000, sharedNextId);

        // Background jobs visit the shared database and then each company's.
        List<String> visited = new ArrayList<>();
        tenantScope.forEachDatabase(() -> visited.add(tenantScope.currentDatabaseKey()));
        assertEquals(TenantScope.SHARED, visited.get(0));
        assertTrue(visited.contains(companyWithOwnDatabase.toString()), visited.toString());
    }

    private String newProduct(String name) {
        return productService.create(new ProductService.NewProduct(null, name, null, null, null, null, null, false, false));
    }

    private long countProductNamed(UUID companyId, String productName) {
        return TenantContext.callAs(companyId, () -> coreJdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name = ?", Long.class, productName));
    }
}
