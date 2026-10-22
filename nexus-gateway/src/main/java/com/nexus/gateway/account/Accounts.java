package com.nexus.gateway.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.gateway.security.SecretBox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Carrier and email accounts per client and store. A store's own account wins; otherwise the client's account for
 * every store (store_id NULL). Secrets (keys, passwords) are stored encrypted and never returned by the API.
 */
@Component
public class Accounts {

    public static final Set<String> PROVIDERS = Set.of("FEDEX", "UPS", "NEXUS_TEST", "SMTP", "TEST_EMAIL");
    static final Set<String> ENVIRONMENTS = Set.of("SANDBOX", "PRODUCTION");
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final SecretBox secretBox;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public Accounts(JdbcTemplate jdbc, SecretBox secretBox) {
        this.jdbc = jdbc;
        this.secretBox = secretBox;
    }

    /** An account ready to use: plain settings and opened secrets. */
    public record Account(String accountId, String provider, String environment, Map<String, String> settings, Map<String, String> secrets) {

        public String setting(String name) {
            return settings.get(name);
        }

        /** A secret that must be there; a missing one is an error naming it. */
        public String secret(String name) {
            String value = secrets.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("The " + provider + " account has no " + name);
            }
            return value;
        }

        /** Anything but PRODUCTION talks to the provider's test environment. */
        public boolean sandbox() {
            return !"PRODUCTION".equalsIgnoreCase(environment);
        }
    }

    /** An account as the API shows it: the names of its secrets, never their values. */
    public record AccountView(String accountId, String storeId, String provider, String environment, Map<String, String> settings,
                              Set<String> secretNames, boolean active) {
    }

    /** Sets the client's account for a provider and store (null: every store), replacing the earlier one. Returns the new account id. */
    public String put(String clientId, String storeId, String provider, String environment, Map<String, String> settings, Map<String, String> secrets) {
        String providerCode = provider == null ? null : provider.trim().toUpperCase(Locale.ROOT);
        if (!PROVIDERS.contains(providerCode)) {
            throw new IllegalArgumentException("Unknown provider " + provider + "; use one of " + PROVIDERS);
        }
        String environmentCode = environment == null ? "SANDBOX" : environment.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(environmentCode)) {
            throw new IllegalArgumentException("Environment is SANDBOX or PRODUCTION");
        }
        String settingsJson = toJson(settings == null ? Map.of() : settings);
        String sealedSecrets = secretBox.seal(toJson(secrets == null ? Map.of() : secrets));

        // The earlier account for the same provider and store stops being used; it is kept for the record.
        if (storeId == null) {
            jdbc.update("UPDATE provider_account SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE client_id = ? AND provider = ? AND store_id IS NULL",
                    clientId, providerCode);
        } else {
            jdbc.update("UPDATE provider_account SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE client_id = ? AND provider = ? AND store_id = ?",
                    clientId, providerCode, storeId);
        }
        String accountId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO provider_account (account_id, client_id, store_id, provider, environment, settings, secret, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)", accountId, clientId, storeId, providerCode, environmentCode, settingsJson, sealedSecrets);
        return accountId;
    }

    /** The store's active account for the provider, else the client's; an error when there is neither. */
    public Account resolve(String clientId, String storeId, String provider) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT account_id, store_id, provider, environment, settings, secret FROM provider_account "
                        + "WHERE client_id = ? AND provider = ? AND active = TRUE AND (store_id = ? OR store_id IS NULL) "
                        + "ORDER BY CASE WHEN store_id IS NULL THEN 1 ELSE 0 END",
                clientId, provider, storeId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("No " + provider + " account is set up" + (storeId == null ? "" : " for store " + storeId));
        }
        Map<String, Object> row = rows.get(0);
        return new Account((String) row.get("account_id"), (String) row.get("provider"), (String) row.get("environment"),
                fromJson((String) row.get("settings")), fromJson(secretBox.open((String) row.get("secret"))));
    }

    /** The client's active accounts, without secret values. */
    public List<AccountView> list(String clientId) {
        return jdbc.queryForList("SELECT account_id, store_id, provider, environment, settings, secret, active FROM provider_account WHERE client_id = ? "
                        + "AND active = TRUE ORDER BY provider, store_id", clientId)
                .stream()
                .map(row -> {
                    Set<String> secretNames = new TreeSet<>(fromJson(secretBox.open((String) row.get("secret"))).keySet());
                    return new AccountView((String) row.get("account_id"), (String) row.get("store_id"), (String) row.get("provider"),
                            (String) row.get("environment"), fromJson((String) row.get("settings")), secretNames, Boolean.TRUE.equals(row.get("active")));
                })
                .toList();
    }

    private String toJson(Map<String, String> values) {
        try {
            return jsonMapper.writeValueAsString(values);
        } catch (JsonProcessingException notSerializable) {
            throw new IllegalArgumentException(notSerializable.getMessage());
        }
    }

    private Map<String, String> fromJson(String json) {
        try {
            return jsonMapper.readValue(json, STRING_MAP);
        } catch (JsonProcessingException corrupt) {
            throw new IllegalStateException("A stored account could not be read", corrupt);
        }
    }
}
