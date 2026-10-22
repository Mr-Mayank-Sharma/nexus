package com.nexus.core.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Keys AI shopping agents use to reach a store (doc 25, feature 1). A key is shown once when issued; the store keeps
 * only its SHA-256 hash (store setting {@code AGENT_API_KEY_HASH}), so a leaked database does not leak working keys.
 * Issuing a new key replaces the old one.
 */
@Service("coreAgentKeyService")
@Transactional
public class AgentKeyService {

    static final String KEY_HASH_SETTING = "AGENT_API_KEY_HASH";
    static final String KEY_PREFIX = "nxa_";
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EntityManager entityManager;
    private final ProductStoreService productStoreService;

    public AgentKeyService(EntityManager entityManager, ProductStoreService productStoreService) {
        this.entityManager = entityManager;
        this.productStoreService = productStoreService;
    }

    /** A new agent key for the store, replacing any earlier one. Shown once; only its hash is kept. */
    public String issueKey(String productStoreId) {
        byte[] randomBytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(randomBytes);
        String key = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        productStoreService.setSetting(productStoreId, KEY_HASH_SETTING, sha256(key));
        return key;
    }

    /** The store an agent key belongs to; empty for a missing or unknown key. */
    @Transactional(readOnly = true)
    public Optional<String> storeForKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return entityManager.createQuery("SELECT s.productStoreId FROM ProductStoreSetting s WHERE s.settingTypeEnumId = :setting "
                        + "AND s.settingValue = :keyHash", String.class)
                .setParameter("setting", KEY_HASH_SETTING)
                .setParameter("keyHash", sha256(key.trim()))
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** SHA-256 of the text, as lower-case hex. */
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException(missingAlgorithm);   // every JVM has SHA-256
        }
    }
}
