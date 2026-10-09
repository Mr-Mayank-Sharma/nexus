package com.nexus.oms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refuses to start a production instance with missing or well-known default secrets.
 *
 * <p>Previously annotated {@code @Lazy} with nothing injecting it, so it never ran. It is now
 * an eager, prod-only bean that throws on startup, which aborts the context.</p>
 */
@Component
@Profile("prod")
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    /** Values shipped as defaults in this repo or in common images. */
    static final Set<String> KNOWN_DEFAULTS = Set.of("nexus", "minioadmin", "changeme", "password", "postgres", "secret");

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${storage.s3.enabled:true}")
    private boolean s3Enabled;

    @Value("${storage.s3.access-key:}")
    private String s3AccessKey;

    @Value("${storage.s3.secret-key:}")
    private String s3SecretKey;

    @PostConstruct
    public void validate() {
        List<String> problems = findProblems(jwtSecret, dbPassword, s3Enabled ? s3AccessKey : "disabled", s3Enabled ? s3SecretKey : "disabled", System.getenv());
        if (!problems.isEmpty()) {
            problems.forEach(p -> log.error("FATAL: {}", p));
            throw new IllegalStateException("Startup validation failed: " + String.join("; ", problems));
        }
        log.info("Startup validation passed: all required secrets are configured.");
    }

    static List<String> findProblems(String jwtSecret, String dbPassword, String s3AccessKey,
                                     String s3SecretKey, Map<String, String> env) {
        List<String> problems = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.isBlank()) {
            problems.add("app.jwt.secret (NEXUS_JWT_SECRET) is not set");
        } else if (jwtSecret.length() < 32) {
            problems.add("app.jwt.secret must be at least 32 characters, was " + jwtSecret.length());
        }
        if (isWeak(dbPassword)) {
            problems.add("spring.datasource.password (NEXUS_DB_PASSWORD) is empty or a known default");
        }
        if (isWeak(s3AccessKey) || isWeak(s3SecretKey)) {
            problems.add("storage.s3 access/secret key is empty or a known default (minioadmin)");
        }
        if (!isCredentialKeyValid(env.get("APP_CREDENTIAL_ENCRYPTION_KEY"))) {
            problems.add("APP_CREDENTIAL_ENCRYPTION_KEY must be at least 16 characters; otherwise integration "
                    + "credentials are encrypted with a hardcoded fallback key");
        }
        return problems;
    }

    private static boolean isWeak(String value) {
        return value == null || value.isBlank() || KNOWN_DEFAULTS.contains(value.toLowerCase());
    }

    /**
     * The credential vault requires a key of at least 16 characters; anything
     * shorter (or absent) makes it fall back to a hardcoded key. Mirrors the
     * threshold enforced in {@code CredentialVault}.
     */
    static boolean isCredentialKeyValid(String key) {
        return key != null && !key.isBlank() && key.length() >= 16;
    }
}
