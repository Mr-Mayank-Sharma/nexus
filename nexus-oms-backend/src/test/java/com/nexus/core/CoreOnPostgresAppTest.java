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

    @Test
    void startsWithBothSchemasAndTheSeededOrderFlow() {
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM " + CoreSchemaMigrator.HISTORY_TABLE + " WHERE success AND version IS NOT NULL AND version <> '0'", Long.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class) >= 75, "legacy migrations ran too");
        assertEquals("ORDER_CREATED", statuses.initialStatus(null, "ORDER_STATUS"));
        assertTrue(statuses.isAllowed(null, "ORDER_HOLD", "ORDER_APPROVED"));
        StatusService.StatusTransitionException e = assertThrows(StatusService.StatusTransitionException.class,
                () -> statuses.requireAllowed(null, "ORDER_COMPLETED", "ORDER_CREATED"));
        assertEquals("Can't change from Completed to Created; nothing can follow Completed.", e.getMessage());
    }
}
