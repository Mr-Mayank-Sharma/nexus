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

    private final DataSource delegate;

    public TenantAwareDataSource(DataSource delegate) {
        this.delegate = delegate;
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

    private void applyTenantContext(Connection connection) {
        try {
            UUID tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET SESSION app.current_tenant_id = '" + tenantId + "'");
                }
                log.trace("Set tenant context on connection: {}", tenantId);
            }
        } catch (IllegalStateException e) {
            log.trace("No tenant context available – leaving connection unscoped");
        } catch (Exception e) {
            log.warn("Failed to set tenant context on connection", e);
        }
    }

    private Connection wrapConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{ Connection.class },
                new ConnectionResetHandler(connection)
        );
    }

    private void resetTenantContext(Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET app.current_tenant_id");
        } catch (SQLException e) {
            log.debug("Could not reset tenant context on connection return", e);
        }
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
