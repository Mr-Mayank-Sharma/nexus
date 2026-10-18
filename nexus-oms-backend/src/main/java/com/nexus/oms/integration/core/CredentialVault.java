package com.nexus.oms.integration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CredentialVault {

    private static final Logger log = LoggerFactory.getLogger(CredentialVault.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    /** The key earlier versions fell back to. It is public in the source, so it is only ever used to read old data (and in local runs). */
    private static final String DEV_FALLBACK = "NexusShipOMS2024";

    /** Encrypts with this key. */
    private final SecretKey key;
    /** Tried, in order, after {@link #key} when opening a value that was sealed by an earlier version. */
    private final List<SecretKey> legacyKeys = new ArrayList<>();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public CredentialVault() {
        this(System.getenv("APP_CREDENTIAL_ENCRYPTION_KEY"), isProduction(System.getenv("SPRING_PROFILES_ACTIVE")));
    }

    /**
     * With a configured key the AES-256 key is derived from the whole value (SHA-256). Earlier versions used only
     * its first 16 characters, so a 32-character hex secret gave 64 bits of strength; those values are still read,
     * and re-sealed with the new key the next time they are saved. Without a configured key a local run uses the
     * public development key, but production refuses to start: data sealed with a key printed in the source is
     * not protected.
     */
    CredentialVault(String envKey, boolean production) {
        SecretKey devKey = new SecretKeySpec(DEV_FALLBACK.getBytes(StandardCharsets.UTF_8), "AES");
        if (envKey != null && envKey.length() >= 16) {
            this.key = new SecretKeySpec(sha256(envKey), "AES");
            legacyKeys.add(new SecretKeySpec(envKey.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES"));
            legacyKeys.add(devKey);
        } else {
            if (production) {
                throw new IllegalStateException("APP_CREDENTIAL_ENCRYPTION_KEY must be set (at least 16 characters) in production. "
                        + "Without it, integration secrets would be sealed with a key that is public in the source code.");
            }
            this.key = devKey;
            log.warn("APP_CREDENTIAL_ENCRYPTION_KEY not set, using the development key. Never do this outside a local run.");
        }
    }

    static boolean isProduction(String profiles) {
        if (profiles == null) return false;
        String p = profiles.toLowerCase(Locale.ROOT);
        return p.contains("prod");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
        List<SecretKey> candidates = new ArrayList<>();
        candidates.add(key);
        candidates.addAll(legacyKeys);
        for (SecretKey candidate : candidates) {
            try {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, candidate, new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH));
                return new String(cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH), StandardCharsets.UTF_8);
            } catch (Exception wrongKey) {
                // GCM authenticates: the wrong key fails here, so the next candidate is tried
            }
        }
        throw new RuntimeException("Decryption failed");
    }

    public void cacheSecret(String key, String value) {
        cache.put(key, encrypt(value));
    }

    public String getCachedSecret(String key) {
        String encrypted = cache.get(key);
        if (encrypted == null) return null;
        return decrypt(encrypted);
    }

    public void evict(String key) { cache.remove(key); }
    public void clear() { cache.clear(); }
}
