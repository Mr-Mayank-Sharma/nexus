package com.nexus.gateway.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Client installations and their API keys. A key is shown once when made; only its SHA-256 hash is kept. */
@Component
public class Clients {

    static final String KEY_PREFIX = "ngw_";
    private static final int KEY_BYTES = 32;

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public Clients(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NewClient(String clientId, String apiKey) {
    }

    /** A new client installation and its first key. */
    public NewClient create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A client needs a name");
        }
        String clientId = UUID.randomUUID().toString();
        String apiKey = newKey();
        jdbc.update("INSERT INTO gateway_client (client_id, client_name, api_key_hash, active) VALUES (?, ?, ?, TRUE)", clientId, name.trim(), sha256(apiKey));
        return new NewClient(clientId, apiKey);
    }

    /** A new key for the client; the old one stops working at once. */
    public String rotate(String clientId) {
        String apiKey = newKey();
        int updated = jdbc.update("UPDATE gateway_client SET api_key_hash = ? WHERE client_id = ?", sha256(apiKey), clientId);
        if (updated == 0) {
            throw new IllegalArgumentException("Unknown client " + clientId);
        }
        return apiKey;
    }

    /** The active client this key belongs to; empty for a missing, unknown or disabled key. */
    public Optional<String> authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return jdbc.queryForList("SELECT client_id FROM gateway_client WHERE api_key_hash = ? AND active = TRUE", String.class, sha256(apiKey.trim()))
                .stream()
                .findFirst();
    }

    private String newKey() {
        byte[] randomBytes = new byte[KEY_BYTES];
        random.nextBytes(randomBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    static String sha256(String apiKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException(missingAlgorithm);   // every JVM has SHA-256
        }
    }
}
