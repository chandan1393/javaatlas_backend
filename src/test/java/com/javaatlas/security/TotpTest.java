package com.javaatlas.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpTest {

    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        // RFC 6238 appendix B, SHA-1, 8 digits
        assertEquals("94287082", Totp.code(RFC_KEY, 59 / 30, 8));
        assertEquals("07081804", Totp.code(RFC_KEY, 1111111109L / 30, 8));
        assertEquals("14050471", Totp.code(RFC_KEY, 1111111111L / 30, 8));
    }

    @Test
    void base32RoundTrip() {
        assertArrayEquals(RFC_KEY, Totp.unbase32(Totp.base32(RFC_KEY)));
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(RFC_KEY));
    }

    @Test
    void verifiesCurrentCodeAndRejectsWrongOnes() {
        String secret = Totp.base32(RFC_KEY);
        Instant t = Instant.ofEpochSecond(1111111111L);
        String six = Totp.code(RFC_KEY, t.getEpochSecond() / 30, 6);
        assertTrue(Totp.verify(secret, six, t).isPresent());
        assertFalse(Totp.verify(secret, "000000", t).isPresent() && !six.equals("000000"));
        assertFalse(Totp.verify(secret, "12ab56", t).isPresent());
    }
}
