package com.nexus.gateway.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Client installations and their API keys. A key is shown once when made; only its SHA-256 hash is kept. */
@Component
public class Clients {

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public Clients(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NewClient(String clientId, String apiKey) {
    }

    public NewClient create(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A client needs a name");
        String id = UUID.randomUUID().toString();
        String key = newKey();
        jdbc.update("INSERT INTO gateway_client (client_id, client_name, api_key_hash, active) VALUES (?, ?, ?, TRUE)", id, name.trim(), hash(key));
        return new NewClient(id, key);
    }

    /** A new key for the client; the old one stops working at once. */
    public String rotate(String clientId) {
        String key = newKey();
        if (jdbc.update("UPDATE gateway_client SET api_key_hash = ? WHERE client_id = ?", hash(key), clientId) == 0) {
            throw new IllegalArgumentException("Unknown client " + clientId);
        }
        return key;
    }

    public Optional<String> authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        List<String> ids = jdbc.queryForList("SELECT client_id FROM gateway_client WHERE api_key_hash = ? AND active = TRUE", String.class, hash(apiKey.trim()));
        return ids.stream().findFirst();
    }

    private String newKey() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return "ngw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
