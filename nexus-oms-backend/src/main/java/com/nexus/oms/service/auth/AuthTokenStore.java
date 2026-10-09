package com.nexus.oms.service.auth;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.nexus.oms.entity.NxAuthToken;
import com.nexus.oms.repository.AuthTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Database-backed store for single-use authentication tokens.
 *
 * <p>Replaces the per-JVM {@code HashMap}s that held MFA sessions, reset tokens and SSO
 * state: those were lost on restart, invisible to other replicas and not thread-safe.
 * Raw tokens are 256-bit random values returned to the caller once; only their SHA-256
 * hash is persisted.</p>
 */
@Service
public class AuthTokenStore {

    public static final String MFA_CHALLENGE = "MFA_CHALLENGE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String SSO_STATE = "SSO_STATE";
    public static final String SSO_EXCHANGE = "SSO_EXCHANGE";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository repository;

    public AuthTokenStore(AuthTokenRepository repository) {
        this.repository = repository;
    }

    /** Issues a new token and returns the raw value; it cannot be recovered later. */
    @Transactional
    public String issue(String type, String username, String payload, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(NxAuthToken.builder()
                .tokenType(type)
                .tokenHash(hash(raw))
                .username(username)
                .payload(payload)
                .expiresAt(LocalDateTime.now().plus(ttl))
                .build());
        return raw;
    }

    /** Returns the live token without consuming it (expired tokens are deleted and hidden). */
    @Transactional
    public Optional<NxAuthToken> find(String type, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Optional<NxAuthToken> token = repository.findByTokenTypeAndTokenHash(type, hash(raw));
        if (token.isPresent() && token.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            repository.delete(token.get());
            return Optional.empty();
        }
        return token;
    }

    /** Looks up and deletes the token in one step, so it can be used exactly once. */
    @Transactional
    public Optional<NxAuthToken> consume(String type, String raw) {
        Optional<NxAuthToken> token = find(type, raw);
        token.ifPresent(repository::delete);
        return token;
    }

    /** Records a failed attempt; returns the new count. */
    @Transactional
    public int recordFailedAttempt(NxAuthToken token) {
        token.setAttempts(token.getAttempts() + 1);
        repository.save(token);
        return token.getAttempts();
    }

    @Transactional
    public void delete(NxAuthToken token) {
        repository.delete(token);
    }

    /** Invalidates every outstanding token of a type for a user (e.g. older reset links). */
    @Transactional
    public void revokeAll(String type, String username) {
        repository.deleteByTypeAndUsername(type, username);
    }

    @Scheduled(cron = "0 */30 * * * *")
    @SchedulerLock(name = "AuthTokenStore.purgeExpired", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(LocalDateTime.now());
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
