package com.nexus.oms.tenancy;

import com.nexus.oms.config.TenantAwareDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What the shared database knows about companies and their sign-ins (doc 29, phase C): the companies, and which
 * company each username and email belongs to. Sign-in reads it before anyone is known, so it works as the database
 * owner, never as a company. Everything else about a user lives in the company's own core database.
 */
@Component
public class PlatformDirectory {

    private final JdbcTemplate sharedJdbc;

    public PlatformDirectory(DataSource sharedDataSource) {
        DataSource unscoped = sharedDataSource instanceof TenantAwareDataSource scoped ? scoped.getDelegate() : sharedDataSource;
        this.sharedJdbc = new JdbcTemplate(unscoped);
    }

    // ── companies ──

    /** Records a new company. */
    public void addCompany(UUID tenantId, String companyName) {
        sharedJdbc.update("INSERT INTO platform_tenant (tenant_id, company_name) VALUES (?, ?)", tenantId, companyName);
    }

    public Optional<String> companyName(UUID tenantId) {
        return sharedJdbc.queryForList("SELECT company_name FROM platform_tenant WHERE tenant_id = ?", String.class, tenantId).stream().findFirst();
    }

    public void renameCompany(UUID tenantId, String companyName) {
        sharedJdbc.update("UPDATE platform_tenant SET company_name = ? WHERE tenant_id = ?", companyName, tenantId);
    }

    /** Every company, for background work that must visit each one. */
    public List<UUID> companies() {
        return sharedJdbc.queryForList("SELECT tenant_id FROM platform_tenant ORDER BY created_at", UUID.class);
    }

    // ── sign-ins ──

    public boolean usernameTaken(String username) {
        return Boolean.TRUE.equals(sharedJdbc.queryForObject("SELECT COUNT(*) > 0 FROM platform_login WHERE username = ?", Boolean.class, username));
    }

    /** Records that the username (and email) signs in to the company. */
    public void addLogin(String username, String email, UUID tenantId) {
        sharedJdbc.update("INSERT INTO platform_login (username, email, tenant_id) VALUES (?, ?, ?)", username, normaliseEmail(email), tenantId);
    }

    public void removeLogin(String username) {
        sharedJdbc.update("DELETE FROM platform_login WHERE username = ?", username);
    }

    public void changeLoginEmail(String username, String email) {
        sharedJdbc.update("UPDATE platform_login SET email = ? WHERE username = ?", normaliseEmail(email), username);
    }

    /** The company a username signs in to. */
    public Optional<UUID> companyOfUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return sharedJdbc.queryForList("SELECT tenant_id FROM platform_login WHERE username = ?", UUID.class, username.trim()).stream().findFirst();
    }

    /** Every sign-in with this email, as (username, company); an email may be used in more than one company. */
    public List<Login> loginsWithEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return sharedJdbc.query("SELECT username, tenant_id FROM platform_login WHERE LOWER(email) = ?",
                (row, number) -> new Login(row.getString("username"), UUID.fromString(row.getString("tenant_id"))), normaliseEmail(email));
    }

    public record Login(String username, UUID tenantId) {
    }

    private static String normaliseEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
