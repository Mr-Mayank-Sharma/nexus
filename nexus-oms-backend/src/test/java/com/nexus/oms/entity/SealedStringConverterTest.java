package com.nexus.oms.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SealedStringConverterTest {

    private final SealedStringConverter converter = new SealedStringConverter();

    @Test
    void sealsOnWriteAndOpensOnRead() {
        String stored = converter.convertToDatabaseColumn("super-secret-token");
        assertTrue(stored.startsWith("enc:v1:"));
        assertFalse(stored.contains("super-secret-token"));
        assertEquals("super-secret-token", converter.convertToEntityAttribute(stored));
    }

    @Test
    void legacyPlaintextIsReadAsIsAndSealedOnNextSave() {
        assertEquals("old-plain", converter.convertToEntityAttribute("old-plain"));
        assertTrue(converter.convertToDatabaseColumn("old-plain").startsWith("enc:v1:"));
    }

    @Test
    void alreadySealedAndNullAreLeftAlone() {
        String stored = converter.convertToDatabaseColumn("x");
        assertEquals(stored, converter.convertToDatabaseColumn(stored));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void bigCommerceTokenIsNeverSerialised() throws Exception {
        NxBigCommerceConfig c = NxBigCommerceConfig.builder().storeHash("h").accessToken("tok-123").build();
        String json = new ObjectMapper().writeValueAsString(c);
        assertFalse(json.contains("tok-123"));
        assertFalse(json.contains("accessToken"));
    }
}
