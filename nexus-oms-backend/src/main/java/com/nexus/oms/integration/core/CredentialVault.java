package com.nexus.oms.integration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CredentialVault {

    private static final Logger log = LoggerFactory.getLogger(CredentialVault.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey key;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public CredentialVault() {
        String envKey = System.getenv("APP_CREDENTIAL_ENCRYPTION_KEY");
        if (envKey != null && envKey.length() >= 16) {
            byte[] keyBytes = envKey.substring(0, 16).getBytes();
            this.key = new SecretKeySpec(keyBytes, "AES");
        } else {
            byte[] fallback = "NexusShipOMS2024".getBytes();
            this.key = new SecretKeySpec(fallback, "AES");
            log.warn("APP_CREDENTIAL_ENCRYPTION_KEY not set, using fallback key. Set this in production!");
        }
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
            return new String(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
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
