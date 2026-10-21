package com.nexus.gateway.account;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.gateway.security.SecretBox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Carrier and email accounts per client and store. A store's own account wins; otherwise the client's account for
 * every store (store_id NULL). Secrets (keys, passwords) are stored encrypted and never returned by the API.
 */
@Component
public class Accounts {

    public static final Set<String> PROVIDERS = Set.of("FEDEX", "UPS", "NEXUS_TEST", "SMTP", "TEST_EMAIL");
    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final SecretBox box;
    private final ObjectMapper json = new ObjectMapper();

    public Accounts(JdbcTemplate jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    /** An account ready to use: plain settings and opened secrets. */
    public record Account(String accountId, String provider, String environment, Map<String, String> settings, Map<String, String> secrets) {
        public String setting(String name) {
            return settings.get(name);
        }

        public String secret(String name) {
            String v = secrets.get(name);
            if (v == null || v.isBlank()) throw new IllegalStateException("The " + provider + " account has no " + name);
            return v;
        }

        public boolean sandbox() {
            return !"PRODUCTION".equalsIgnoreCase(environment);
        }
    }

    public record AccountView(String accountId, String storeId, String provider, String environment, Map<String, String> settings,
                              Set<String> secretNames, boolean active) {
    }

    /** Sets the client's account for a provider and store (null: every store), replacing the earlier one. */
    public String put(String clientId, String storeId, String provider, String environment, Map<String, String> settings, Map<String, String> secrets) {
        String p = provider == null ? null : provider.trim().toUpperCase();
        if (!PROVIDERS.contains(p)) throw new IllegalArgumentException("Unknown provider " + provider + "; use one of " + PROVIDERS);
        String env = environment == null ? "SANDBOX" : environment.trim().toUpperCase();
        if (!Set.of("SANDBOX", "PRODUCTION").contains(env)) throw new IllegalArgumentException("Environment is SANDBOX or PRODUCTION");
        try {
            String s = json.writeValueAsString(settings == null ? Map.of() : settings);
            String sec = box.seal(json.writeValueAsString(secrets == null ? Map.of() : secrets));
            jdbc.update("UPDATE provider_account SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE client_id = ? AND provider = ? AND "
                    + (storeId == null ? "store_id IS NULL" : "store_id = ?"), storeId == null ? new Object[]{clientId, p} : new Object[]{clientId, p, storeId});
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO provider_account (account_id, client_id, store_id, provider, environment, settings, secret, active) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)",
                    id, clientId, storeId, p, env, s, sec);
            return id;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public Account resolve(String clientId, String storeId, String provider) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT account_id, store_id, provider, environment, settings, secret FROM provider_account "
                + "WHERE client_id = ? AND provider = ? AND active = TRUE AND (store_id = ? OR store_id IS NULL) ORDER BY CASE WHEN store_id IS NULL THEN 1 ELSE 0 END",
                clientId, provider, storeId);
        if (rows.isEmpty()) throw new IllegalStateException("No " + provider + " account is set up" + (storeId == null ? "" : " for store " + storeId));
        Map<String, Object> r = rows.get(0);
        try {
            return new Account((String) r.get("account_id"), (String) r.get("provider"), (String) r.get("environment"),
                    json.readValue((String) r.get("settings"), MAP), json.readValue(box.open((String) r.get("secret")), MAP));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("A stored account could not be read", e);
        }
    }

    /** The client's active accounts, without secret values. */
    public List<AccountView> list(String clientId) {
        return jdbc.queryForList("SELECT account_id, store_id, provider, environment, settings, secret, active FROM provider_account WHERE client_id = ? "
                + "AND active = TRUE ORDER BY provider, store_id", clientId).stream().map(r -> {
            try {
                Map<String, String> secrets = json.readValue(box.open((String) r.get("secret")), MAP);
                return new AccountView((String) r.get("account_id"), (String) r.get("store_id"), (String) r.get("provider"), (String) r.get("environment"),
                        json.readValue((String) r.get("settings"), MAP), new java.util.TreeSet<>(secrets.keySet()), Boolean.TRUE.equals(r.get("active")));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    static Map<String, String> copy(Map<String, String> m) {
        return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
    }
}
