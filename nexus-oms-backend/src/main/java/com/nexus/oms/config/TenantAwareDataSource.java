package com.nexus.oms.config;

import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * DataSource wrapper that sets {@code app.current_tenant_id} on every
 * JDBC connection borrowed from the pool, enabling PostgreSQL Row-Level
 * Security enforcement at the database level.
 *
 * <p>Uses {@code SET SESSION} (not {@code SET LOCAL}) because the
 * variable must persist across the Hibernate-managed transaction that
 * begins <em>after</em> the connection is obtained from the pool.
 * The setting is reset to empty when the connection is returned.</p>
 *
 * <p>This is a defence-in-depth layer: even if application code omits a
 * {@code WHERE tenant_id = ?} clause, the database will silently filter
 * rows to the authenticated tenant.</p>
 */
public class TenantAwareDataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);

    private static final String SET_SESSION_SQL = "SET SESSION app.current_tenant_id = '%s'";
    private static final String RESET_SESSION_SQL = "RESET app.current_tenant_id; RESET app.current_user";
    /** Who is acting, for the order-history trigger (V87). Only plain user names are passed through. */
    private static final java.util.regex.Pattern USER_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9._@+-]{1,100}");
    private static final String SET_H2_SQL = "SET @app_current_tenant_id = '%s'";
    private static final String RESET_H2_SQL = "SET @app_current_tenant_id = NULL";

    /** Non-superuser role (created by V69) that tenant-scoped connections switch to. */
    public static final String DEFAULT_APP_ROLE = "nexus_app";
    private static final java.util.regex.Pattern ROLE_NAME = java.util.regex.Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final DataSource delegate;
    /**
     * Superusers and table owners without FORCE bypass Row-Level Security, so policies alone
     * protect nothing when the app logs in as the database owner. Every connection used on
     * behalf of a tenant therefore runs {@code SET ROLE <appRole>}; RLS then applies to
     * every query, including the ~280 {@code findById} lookups that carry no tenant filter.
     * Empty disables role switching (only for databases where the login role is already
     * a non-owner, non-superuser).
     */
    private final String appRole;
    private Boolean isH2; // null = not yet determined

    public TenantAwareDataSource(DataSource delegate) {
        this(delegate, DEFAULT_APP_ROLE);
    }

    public TenantAwareDataSource(DataSource delegate, String appRole) {
        this.delegate = delegate;
        if (appRole != null && !appRole.isBlank() && !ROLE_NAME.matcher(appRole).matches()) {
            throw new IllegalArgumentException("Invalid nexus.rls.app-role: " + appRole);
        }
        this.appRole = appRole == null ? "" : appRole;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = delegate.getConnection();
        applyTenantContext(connection);
        return wrapConnection(connection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = delegate.getConnection(username, password);
        applyTenantContext(connection);
        return wrapConnection(connection);
    }

    /** The unscoped pool, for schema migrations, which must never run as a tenant. */
    public DataSource getDelegate() {
        return delegate;
    }

    private void applyTenantContext(Connection connection) throws SQLException {
        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenantId();
        } catch (IllegalStateException e) {
            log.trace("No tenant context available – leaving connection unscoped");
            return;
        }
        if (tenantId == null) {
            return;
        }
        boolean h2 = isH2(connection);
        try (Statement stmt = connection.createStatement()) {
            String user = h2 ? null : currentUserName();
            stmt.execute(h2 ? String.format(SET_H2_SQL, tenantId) : String.format(SET_SESSION_SQL, tenantId)
                    + (user != null ? "; SET SESSION app.current_user = '" + user + "'" : ""));
            if (!h2 && !appRole.isEmpty()) {
                stmt.execute("SET ROLE " + appRole);
            }
        } catch (SQLException e) {
            // Fail closed: a tenant request must never run on an unscoped, RLS-bypassing session.
            connection.close();
            throw new SQLException("Could not scope connection to tenant " + tenantId
                    + " (is role '" + appRole + "' created by migration V69?)", e);
        }
        log.trace("Set tenant context on connection: {}", tenantId);
    }

    private static String currentUserName() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return null;
        return USER_NAME.matcher(auth.getName()).matches() ? auth.getName() : null;
    }

    private Connection wrapConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{ Connection.class },
                new ConnectionResetHandler(connection)
        );
    }

    private void resetTenantContext(Connection connection) {
        boolean h2 = isH2(connection);
        try (Statement stmt = connection.createStatement()) {
            if (!h2 && !appRole.isEmpty()) {
                stmt.execute("RESET ROLE");
            }
            stmt.execute(h2 ? RESET_H2_SQL : RESET_SESSION_SQL);
        } catch (SQLException e) {
            log.debug("Could not reset tenant context on connection return", e);
        }
    }

    private boolean isH2(Connection connection) {
        if (isH2 == null) {
            try {
                String productName = connection.getMetaData().getDatabaseProductName();
                isH2 = productName != null && productName.contains("H2");
                log.debug("Database product: {} → isH2={}", productName, isH2);
            } catch (SQLException e) {
                log.warn("Could not determine database product name", e);
                isH2 = false;
            }
        }
        return isH2;
    }

    // ── Delegating methods ──────────────────────────────────────────

    @Override public java.io.PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(java.io.PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public java.util.logging.Logger getParentLogger() {
        try { return delegate.getParentLogger(); }
        catch (java.sql.SQLException e) { throw new RuntimeException(e); }
    }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }

    // ── Connection proxy handler ────────────────────────────────────

    private class ConnectionResetHandler implements InvocationHandler {
        private final Connection delegate;
        private boolean closed = false;

        ConnectionResetHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                if (!closed) {
                    closed = true;
                    resetTenantContext(delegate);
                }
                delegate.close();
                return null;
            }
            return method.invoke(delegate, args);
        }
    }
}
