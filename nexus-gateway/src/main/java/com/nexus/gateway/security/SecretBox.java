package com.nexus.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts stored credentials with AES-256-GCM. The key is derived from {@code NEXUS_GATEWAY_MASTER_KEY}, which lives
 * only in the gateway's environment; the gateway refuses to start without it. Values look like {@code v1:<base64>},
 * where the base64 holds a random 12-byte nonce followed by the ciphertext and its tag.
 */
@Component
public class SecretBox {

    static final String FORMAT_PREFIX = "v1:";
    static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_MASTER_KEY_LENGTH = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${nexus.gateway.master-key:}") String masterKey) {
        if (masterKey == null || masterKey.length() < MIN_MASTER_KEY_LENGTH) {
            throw new IllegalStateException("Set NEXUS_GATEWAY_MASTER_KEY (at least 32 characters) before starting the gateway");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException(missingAlgorithm);   // every JVM has SHA-256
        }
    }

    /** Encrypts the text with a fresh nonce; null stays null. */
    public String seal(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] nonceAndCipherText = ByteBuffer.allocate(nonce.length + cipherText.length).put(nonce).put(cipherText).array();
            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(nonceAndCipherText);
        } catch (Exception encryptionFailure) {
            throw new IllegalStateException("Could not encrypt", encryptionFailure);
        }
    }

    /** Decrypts a sealed value; null stays null. A value sealed with another master key fails with a hint. */
    public String open(String sealed) {
        if (sealed == null) {
            return null;
        }
        if (!sealed.startsWith(FORMAT_PREFIX)) {
            throw new IllegalStateException("Unknown secret format");
        }
        try {
            byte[] nonceAndCipherText = Base64.getDecoder().decode(sealed.substring(FORMAT_PREFIX.length()));
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonceAndCipherText, 0, NONCE_BYTES));
            byte[] plainBytes = cipher.doFinal(nonceAndCipherText, NONCE_BYTES, nonceAndCipherText.length - NONCE_BYTES);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception decryptionFailure) {
            throw new IllegalStateException("Could not decrypt a stored secret; was NEXUS_GATEWAY_MASTER_KEY changed?", decryptionFailure);
        }
    }
}
