package com.nexus.oms.integration.core;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Integration secrets are the most sensitive thing Nexus holds, and it is cheapest to get this right before there is data. */
class CredentialVaultKeyTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";   // 32 hex chars, as `openssl rand -hex 16` gives

    /** The previous algorithm exactly: AES-128, key = first 16 characters, iv|ciphertext in Base64. */
    private static String sealTheOldWay(String keyText, String plain) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyText.getBytes(StandardCharsets.UTF_8), "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(all);
    }

    @Test
    void roundTripsWithAConfiguredKey() {
        CredentialVault vault = new CredentialVault(KEY, true);
        assertEquals("shpat_secret", vault.decrypt(vault.encrypt("shpat_secret")));
    }

    @Test
    void theKeyUsesTheWholeSecret_notJustItsFirstSixteenCharacters() throws Exception {
        CredentialVault a = new CredentialVault("0123456789abcdefAAAAAAAAAAAAAAAA", true);
        CredentialVault b = new CredentialVault("0123456789abcdefBBBBBBBBBBBBBBBB", true);   // same first 16 characters

        String sealed = a.encrypt("token");

        assertThrows(RuntimeException.class, () -> b.decrypt(sealed), "a different secret must not open it");
    }

    @Test
    void valuesSealedByEarlierVersionsStillOpen() throws Exception {
        CredentialVault vault = new CredentialVault(KEY, true);

        assertEquals("old-token", vault.decrypt(sealTheOldWay(KEY.substring(0, 16), "old-token")));
        assertEquals("dev-token", vault.decrypt(sealTheOldWay("NexusShipOMS2024", "dev-token")), "sealed in a local run with the public key");
    }

    @Test
    void newValuesCannotBeOpenedWithTheOldShortKey() throws Exception {
        String sealed = new CredentialVault(KEY, true).encrypt("new-token");
        byte[] all = Base64.getDecoder().decode(sealed);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES"), new GCMParameterSpec(128, all, 0, 12));

        assertThrows(Exception.class, () -> c.doFinal(all, 12, all.length - 12));
    }

    @Test
    void productionRefusesToStartWithoutAKey() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new CredentialVault(null, true));
        assertTrue(e.getMessage().contains("APP_CREDENTIAL_ENCRYPTION_KEY"));
        assertThrows(IllegalStateException.class, () -> new CredentialVault("tooshort", true));
    }

    @Test
    void aLocalRunWithoutAKeyStillWorks_andReadsItsOwnValues() {
        CredentialVault dev = new CredentialVault(null, false);
        assertEquals("x", dev.decrypt(dev.encrypt("x")));
    }

    @Test
    void anUnknownValueFailsInsteadOfReturningGarbage() {
        CredentialVault vault = new CredentialVault(KEY, true);
        assertThrows(RuntimeException.class, () -> vault.decrypt(Base64.getEncoder().encodeToString(new byte[40])));
        assertThrows(RuntimeException.class, () -> vault.decrypt("not base64 !!"));
    }

    @Test
    void profilesThatMeanProduction() {
        assertTrue(CredentialVault.isProduction("prod"));
        assertTrue(CredentialVault.isProduction("prod,aws"));
        assertFalse(CredentialVault.isProduction("dev"));
        assertFalse(CredentialVault.isProduction(null));
    }
}
