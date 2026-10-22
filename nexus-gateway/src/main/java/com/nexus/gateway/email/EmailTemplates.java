package com.nexus.gateway.email;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Email templates ({@code email_template}) per client, optionally per store, and which email account a client uses
 * ({@code provider_account}). A store's own template or account wins over the client's.
 */
@Repository
class EmailTemplates {

    private final JdbcTemplate jdbc;

    EmailTemplates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Template(String subject, String bodyHtml) {
    }

    /** Replaces the client's (or the store's, when given) template with this id. */
    void put(String clientId, String storeId, String templateId, String subject, String bodyHtml) {
        if (storeId == null) {
            jdbc.update("DELETE FROM email_template WHERE client_id = ? AND template_id = ? AND store_id IS NULL", clientId, templateId);
        } else {
            jdbc.update("DELETE FROM email_template WHERE client_id = ? AND template_id = ? AND store_id = ?", clientId, templateId, storeId);
        }
        jdbc.update("INSERT INTO email_template (template_row_id, client_id, store_id, template_id, subject, body_html) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), clientId, storeId, templateId, subject, bodyHtml);
    }

    /** The store's template, else the client's. */
    Optional<Template> find(String clientId, String storeId, String templateId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT subject, body_html FROM email_template WHERE client_id = ? AND template_id = ? "
                + "AND (store_id = ? OR store_id IS NULL) ORDER BY CASE WHEN store_id IS NULL THEN 1 ELSE 0 END", clientId, templateId, storeId);
        return rows.stream().findFirst().map(row -> new Template((String) row.get("subject"), (String) row.get("body_html")));
    }

    /** The email provider (SMTP or TEST_EMAIL) of the store's active account, else the client's. */
    Optional<String> emailProvider(String clientId, String storeId) {
        List<String> providers = jdbc.queryForList("SELECT provider FROM provider_account WHERE client_id = ? AND active = TRUE "
                + "AND provider IN ('SMTP', 'TEST_EMAIL') AND (store_id = ? OR store_id IS NULL) ORDER BY CASE WHEN store_id IS NULL THEN 1 ELSE 0 END",
                String.class, clientId, storeId);
        return providers.stream().findFirst();
    }
}
