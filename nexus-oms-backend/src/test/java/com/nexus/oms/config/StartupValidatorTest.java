package com.nexus.oms.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StartupValidatorTest {

    private static final String STRONG_JWT = "0123456789abcdef0123456789abcdef-strong";
    private static final Map<String, String> GOOD_ENV = Map.of("APP_CREDENTIAL_ENCRYPTION_KEY", "0123456789abcdef");

    @Test
    void strongConfiguration_hasNoProblems() {
        assertTrue(StartupValidator.findProblems(STRONG_JWT, "S3cure-db-pass", "AKIAEXAMPLE", "s3-secret-value", GOOD_ENV).isEmpty());
    }

    @Test
    void repoDefaults_areAllRejected() {
        List<String> problems = StartupValidator.findProblems("", "nexus", "minioadmin", "minioadmin", Map.of());
        assertEquals(4, problems.size(), problems.toString());
    }

    @Test
    void shortJwtSecret_isRejected() {
        List<String> problems = StartupValidator.findProblems("short", "S3cure-db-pass", "AKIAEXAMPLE", "s3-secret-value", GOOD_ENV);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("32"));
    }

    @Test
    void credentialKeyThreshold() {
        assertFalse(StartupValidator.isCredentialKeyValid(null));
        assertFalse(StartupValidator.isCredentialKeyValid("too-short"));
        assertTrue(StartupValidator.isCredentialKeyValid("0123456789abcdef"));
    }
}
