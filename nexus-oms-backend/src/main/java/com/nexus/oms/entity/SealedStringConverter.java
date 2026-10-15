package com.nexus.oms.entity;

import com.nexus.oms.integration.core.CredentialVault;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Seals a secret column with AES-GCM on write and opens it on read. A value without the
 * {@code enc:v1:} prefix is legacy plaintext: it is returned as is and sealed on the next save.
 */
@Converter
public class SealedStringConverter implements AttributeConverter<String, String> {

    static final String SEALED_PREFIX = "enc:v1:";

    private final CredentialVault vault = new CredentialVault();

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null || plain.isEmpty() || plain.startsWith(SEALED_PREFIX)) return plain;
        return SEALED_PREFIX + vault.encrypt(plain);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored != null && stored.startsWith(SEALED_PREFIX)
                ? vault.decrypt(stored.substring(SEALED_PREFIX.length())) : stored;
    }
}
