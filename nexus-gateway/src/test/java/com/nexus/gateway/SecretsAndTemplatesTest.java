package com.nexus.gateway;

import com.nexus.gateway.security.SecretBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretsAndTemplatesTest {

    @Test
    void sealedSecretsOpenOnlyWithTheSameKey() {
        SecretBox box = new SecretBox("a-master-key-of-at-least-thirty-two-chars");
        String sealed = box.seal("{\"clientSecret\":\"abc\"}");
        assertNotEquals(sealed, box.seal("{\"clientSecret\":\"abc\"}"), "a fresh IV every time");
        assertEquals("{\"clientSecret\":\"abc\"}", box.open(sealed));
        SecretBox other = new SecretBox("another-master-key-of-at-least-32-chars");
        assertThrows(IllegalStateException.class, () -> other.open(sealed));
        assertThrows(IllegalStateException.class, () -> new SecretBox("short"));
    }
}
