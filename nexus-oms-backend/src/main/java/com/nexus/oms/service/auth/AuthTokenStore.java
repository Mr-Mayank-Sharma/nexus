package com.nexus.oms.service.auth;

import com.nexus.oms.config.TenantAwareDataSource;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-use sign-in tokens (MFA challenges, password resets, SSO state) in {@code platform_auth_token} of the shared
 * database: they are used before anyone is signed in (doc 29, phase C). Raw tokens are 256-bit random values returned
 * to the caller once; only their SHA-256 hash is kept.
 */
@Service
public class AuthTokenStore {

    public static final String MFA_CHALLENGE = "MFA_CHALLENGE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String SSO_STATE = "SSO_STATE";
    public static final String SSO_EXCHANGE = "SSO_EXCHANGE";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    /** A stored token: who it is for, what it carries, when it expires, and failed attempts so far. */
    public record AuthToken(UUID id, String tokenType, String username, String payload, LocalDateTime expiresAt, int attempts) {
    }

    private static final RowMapper<AuthToken> TOKEN_ROW = (row, number) -> new AuthToken(UUID.fromString(row.getString("id")),
            row.getString("token_type"), row.getString("username"), row.getString("payload"), row.getTimestamp("expires_at").toLocalDateTime(),
            row.getInt("attempts"));

    private final JdbcTemplate sharedJdbc;

    public AuthTokenStore(DataSource sharedDataSource) {
        DataSource unscoped = sharedDataSource instanceof TenantAwareDataSource scoped ? scoped.getDelegate() : sharedDataSource;
        this.sharedJdbc = new JdbcTemplate(unscoped);
    }

    /** Issues a new token and returns the raw value; it cannot be recovered later. */
    public String issue(String type, String username, String payload, Duration timeToLive) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(randomBytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        LocalDateTime now = LocalDateTime.now();
        sharedJdbc.update("INSERT INTO platform_auth_token (id, token_type, token_hash, username, payload, attempts, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?, ?)", UUID.randomUUID(), type, hash(raw), username, payload,
                Timestamp.valueOf(now.plus(timeToLive)), Timestamp.valueOf(now));
        return raw;
    }

    /** The live token, without using it up; an expired one is deleted and not returned. */
    public Optional<AuthToken> find(String type, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthToken> token = sharedJdbc.query("SELECT id, token_type, username, payload, expires_at, attempts FROM platform_auth_token "
                + "WHERE token_type = ? AND token_hash = ?", TOKEN_ROW, type, hash(raw)).stream().findFirst();
        if (token.isPresent() && token.get().expiresAt().isBefore(LocalDateTime.now())) {
            delete(token.get());
            return Optional.empty();
        }
        return token;
    }

    /** Looks up and deletes the token in one step, so it can be used exactly once. */
    public Optional<AuthToken> consume(String type, String raw) {
        Optional<AuthToken> token = find(type, raw);
        token.ifPresent(this::delete);
        return token;
    }

    /** Records a failed attempt; returns the new count. */
    public int recordFailedAttempt(AuthToken token) {
        sharedJdbc.update("UPDATE platform_auth_token SET attempts = attempts + 1 WHERE id = ?", token.id());
        return token.attempts() + 1;
    }

    public void delete(AuthToken token) {
        sharedJdbc.update("DELETE FROM platform_auth_token WHERE id = ?", token.id());
    }

    /** Invalidates every outstanding token of a type for a user (e.g. older reset links). */
    public void revokeAll(String type, String username) {
        sharedJdbc.update("DELETE FROM platform_auth_token WHERE token_type = ? AND username = ?", type, username);
    }

    @Scheduled(cron = "0 */30 * * * *")
    @SchedulerLock(name = "AuthTokenStore.purgeExpired", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void purgeExpired() {
        sharedJdbc.update("DELETE FROM platform_auth_token WHERE expires_at < ?", Timestamp.valueOf(LocalDateTime.now()));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException("SHA-256 unavailable", missingAlgorithm);
        }
    }
}
