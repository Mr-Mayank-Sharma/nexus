package com.nexus.oms.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs Flyway on the raw pool, never through {@link TenantAwareDataSource}. Migrations are
 * schema operations by the owner: if a tenant happened to be bound to the thread at startup
 * (a leaked security context, a test), the scoped wrapper would SET ROLE nexus_app — which
 * cannot create tables, and before V69 does not even exist.
 */
@Configuration
public class FlywayDataSourceConfig {

    @Bean
    public FlywayConfigurationCustomizer unscopedFlywayDataSource(DataSource dataSource) {
        return configuration -> {
            if (dataSource instanceof TenantAwareDataSource scoped) {
                configuration.dataSource(scoped.getDelegate());
            }
        };
    }
}
