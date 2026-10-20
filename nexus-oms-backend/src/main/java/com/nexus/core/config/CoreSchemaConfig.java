package com.nexus.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the core migrations right after the legacy ones, before Hibernate validates the schema. Turned off with
 * {@code nexus.core.schema.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "nexus.core.schema.enabled", havingValue = "true", matchIfMissing = true)
public class CoreSchemaConfig {

    @Bean
    public FlywayMigrationStrategy legacyThenCore() {
        return flyway -> {
            flyway.migrate();
            CoreSchemaMigrator.migrate(flyway.getConfiguration().getDataSource());
        };
    }
}
