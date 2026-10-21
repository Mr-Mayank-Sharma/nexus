package com.nexus.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts stored credentials with AES-256-GCM. The key is derived from {@code NEXUS_GATEWAY_MASTER_KEY}, which lives
 * only in the gateway's environment; the gateway refuses to start without it. Values look like {@code v1:<base64>}.
 */
@Component
public class SecretBox {

    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${nexus.gateway.master-key:}") String masterKey) {
        if (masterKey == null || masterKey.length() < 32) {
            throw new IllegalStateException("Set NEXUS_GATEWAY_MASTER_KEY (at least 32 characters) before starting the gateway");
        }
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String seal(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt", e);
        }
    }

    public String open(String sealed) {
        if (sealed == null) return null;
        if (!sealed.startsWith("v1:")) throw new IllegalStateException("Unknown secret format");
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(3));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt a stored secret; was NEXUS_GATEWAY_MASTER_KEY changed?", e);
        }
    }
}
