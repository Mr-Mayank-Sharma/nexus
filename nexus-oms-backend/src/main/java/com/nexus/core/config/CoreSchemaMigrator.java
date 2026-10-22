package com.nexus.core.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Migrates the core schema (db/core/common plus db/core/postgresql or db/core/mysql) with its own history table,
 * {@code flyway_core_history}, separate from the legacy migrations. The same core migrations build an empty
 * PostgreSQL or MySQL database from scratch.
 */
public final class CoreSchemaMigrator {

    public static final String HISTORY_TABLE = "flyway_core_history";

    private CoreSchemaMigrator() {
    }

    public static MigrateResult migrate(DataSource dataSource) {
        String vendor = vendor(dataSource);
        return Flyway.configure()
                .dataSource(dataSource)
                .table(HISTORY_TABLE)
                .locations("classpath:db/core/common", "classpath:db/core/" + vendor)
                // the database already holds legacy tables; the core history starts empty
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    /** "postgresql" or "mysql", from the connection; anything else is refused rather than guessed. */
    public static String vendor(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (product.contains("postgres")) {
                return "postgresql";
            }
            if (product.contains("mysql") || product.contains("mariadb")) {
                return "mysql";
            }
            throw new IllegalStateException("The Nexus core schema supports PostgreSQL and MySQL, not " + product);
        } catch (SQLException unreadable) {
            throw new IllegalStateException("Could not read the database type", unreadable);
        }
    }
}
