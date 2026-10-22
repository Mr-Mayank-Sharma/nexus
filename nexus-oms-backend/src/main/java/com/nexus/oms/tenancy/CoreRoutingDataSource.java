package com.nexus.oms.tenancy;

import com.nexus.oms.security.TenantContext;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * The core's data source (doc 29, phase B): every connection goes to the signed-in company's own database, or to the
 * shared database when the company has none yet (and when no one is signed in: start-up, background jobs that have not
 * picked a company, the tests).
 */
public class CoreRoutingDataSource extends AbstractDataSource {

    private final DataSource sharedDataSource;
    private final TenantDatabaseRegistry registry;

    public CoreRoutingDataSource(DataSource sharedDataSource, TenantDatabaseRegistry registry) {
        this.sharedDataSource = sharedDataSource;
        this.registry = registry;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return currentTarget().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return currentTarget().getConnection(username, password);
    }

    /** The shared database, which the core migrates and validates against at start-up. */
    public DataSource sharedDataSource() {
        return sharedDataSource;
    }

    private DataSource currentTarget() {
        UUID tenantId = TenantContext.findCurrentTenantId();
        return registry.dataSourceFor(tenantId).orElse(sharedDataSource);
    }
}
