package com.nexus.oms.service.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    /** RFC 6238 Appendix B shared secret for HMAC-SHA1. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        // Appendix B lists 8-digit values; the 6-digit code is the last six digits.
        long[][] vectors = {
                {59L, 94287082L},
                {1111111109L, 7081804L},
                {1111111111L, 14050471L},
                {1234567890L, 89005924L},
                {2000000000L, 69279037L},
        };
        for (long[] v : vectors) {
            String expected = String.format("%06d", v[1] % 1_000_000);
            assertEquals(expected, TotpService.generate(RFC_KEY, v[0] / 30), "t=" + v[0]);
        }
    }

    @Test
    void base32RoundTrips() {
        String encoded = TotpService.base32Encode(RFC_KEY);
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", encoded);
        assertArrayEquals(RFC_KEY, TotpService.base32Decode(encoded));
    }

    @Test
    void verifyAcceptsAdjacentStepsOnly() {
        String secret = TotpService.base32Encode(RFC_KEY);
        TotpService atT59 = new TotpService(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));
        String current = TotpService.generate(RFC_KEY, 1);
        String previous = TotpService.generate(RFC_KEY, 0);
        String farAway = TotpService.generate(RFC_KEY, 10);

        assertTrue(atT59.verify(secret, current));
        assertTrue(atT59.verify(secret, previous));
        assertFalse(atT59.verify(secret, farAway));
        assertFalse(atT59.verify(secret, "12345"));
        assertFalse(atT59.verify(secret, null));
    }

    @Test
    void generatedSecretsAreUniqueAndDecodable() {
        TotpService service = new TotpService();
        String a = service.generateSecret();
        String b = service.generateSecret();
        assertNotEquals(a, b);
        assertEquals(20, TotpService.base32Decode(a).length);
        assertTrue(service.otpauthUri("Nexus", "a@b.com", a).contains("secret=" + a));
    }
}
