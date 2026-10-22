package com.nexus.oms.tenancy;

import com.nexus.core.config.CoreSchemaMigrator;
import com.nexus.oms.config.TenantAwareDataSource;
import com.nexus.oms.integration.core.CredentialVault;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Where each company's core database is (doc 29, phase B), from {@code platform_tenant_database} in the shared
 * database. A company without a row works in the shared database. Connection pools are opened on first use, and the
 * core schema is migrated before a pool is handed out, so a database is never used before its tables exist.
 */
@Component
public class TenantDatabaseRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantDatabaseRegistry.class);
    static final String ACTIVE = "ACTIVE";
    /** Only plain database names are created, so a name can never carry SQL. */
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    /** Where a company's database is; the password is opened only when a pool is made. */
    record TenantDatabase(UUID tenantId, String jdbcUrl, String username, String passwordSealed) {
    }

    /** The shared database as its owner, never as a company. */
    private final JdbcTemplate sharedJdbc;
    private final CredentialVault credentialVault;
    private final int poolSize;
    /** Rows read from the shared database, refreshed on every change made here. */
    private volatile Map<UUID, TenantDatabase> databaseByTenant = Map.of();
    private final Map<UUID, HikariDataSource> poolByTenant = new ConcurrentHashMap<>();

    public TenantDatabaseRegistry(DataSource sharedDataSource, CredentialVault credentialVault,
                                  @Value("${nexus.tenancy.pool-size:5}") int poolSize) {
        // Owner work (reading the registry before anyone is known, CREATE DATABASE) never runs as a company: a company
        // connection switches to the row-level-security role, which may not create databases.
        DataSource unscoped = sharedDataSource instanceof TenantAwareDataSource scoped ? scoped.getDelegate() : sharedDataSource;
        this.sharedJdbc = new JdbcTemplate(unscoped);
        this.credentialVault = credentialVault;
        this.poolSize = poolSize;
    }

    /** Reads the registry once the application is up (after the migrations). */
    @EventListener(ContextRefreshedEvent.class)
    public void loadAtStartUp() {
        reload();
    }

    /** Reads the registry; called at start-up and after every change. A missing table (before V89) means no company has one. */
    public synchronized void reload() {
        try {
            List<TenantDatabase> rows = sharedJdbc.query("SELECT tenant_id, jdbc_url, username, password_sealed FROM platform_tenant_database "
                            + "WHERE status = ?",
                    (resultSet, rowNumber) -> new TenantDatabase(UUID.fromString(resultSet.getString("tenant_id")), resultSet.getString("jdbc_url"),
                            resultSet.getString("username"), resultSet.getString("password_sealed")), ACTIVE);
            Map<UUID, TenantDatabase> loaded = new ConcurrentHashMap<>();
            rows.forEach(row -> loaded.put(row.tenantId(), row));
            databaseByTenant = loaded;
        } catch (RuntimeException tableMissing) {
            log.debug("No tenant database registry yet: {}", tableMissing.getMessage());
            databaseByTenant = Map.of();
        }
    }

    /** The companies that have a database of their own. */
    public List<UUID> tenantsWithOwnDatabase() {
        return List.copyOf(databaseByTenant.keySet());
    }

    public boolean hasOwnDatabase(UUID tenantId) {
        return tenantId != null && databaseByTenant.containsKey(tenantId);
    }

    /** The company's pool, opened (and its core schema migrated) on first use; empty when it uses the shared database. */
    public Optional<DataSource> dataSourceFor(UUID tenantId) {
        if (!hasOwnDatabase(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(poolByTenant.computeIfAbsent(tenantId, this::openPool));
    }

    /**
     * Gives a company its own database on the same server as the shared one: creates the database, records it, and
     * migrates the core schema into it. Returns the JDBC URL.
     */
    public synchronized String provision(UUID tenantId, String sharedJdbcUrl, String username, String password) {
        if (hasOwnDatabase(tenantId)) {
            return databaseByTenant.get(tenantId).jdbcUrl();
        }
        String databaseName = "nexus_t_" + tenantId.toString().replace("-", "").substring(0, 12);
        if (!DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalStateException("Not a valid database name: " + databaseName);
        }
        boolean exists = Boolean.TRUE.equals(sharedJdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)",
                Boolean.class, databaseName));
        if (!exists) {
            sharedJdbc.execute("CREATE DATABASE " + databaseName);
        }
        String jdbcUrl = sharedJdbcUrl.replaceFirst("/[^/?]+(\\?|$)", "/" + databaseName + "$1");
        sharedJdbc.update("INSERT INTO platform_tenant_database (tenant_id, jdbc_url, username, password_sealed, status) VALUES (?, ?, ?, ?, ?)",
                tenantId, jdbcUrl, username, credentialVault.encrypt(password), ACTIVE);
        reload();
        dataSourceFor(tenantId);   // opens the pool, which migrates the schema
        log.info("Company {} now has its own core database {}", tenantId, databaseName);
        return jdbcUrl;
    }

    private HikariDataSource openPool(UUID tenantId) {
        TenantDatabase database = databaseByTenant.get(tenantId);
        HikariConfig config = new HikariConfig();
        config.setPoolName("core-" + tenantId.toString().substring(0, 8));
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.username());
        config.setPassword(credentialVault.decrypt(database.passwordSealed()));
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(1);
        HikariDataSource pool = new HikariDataSource(config);
        CoreSchemaMigrator.migrate(pool);
        return pool;
    }

    @PreDestroy
    void closePools() {
        poolByTenant.values().forEach(HikariDataSource::close);
    }
}
