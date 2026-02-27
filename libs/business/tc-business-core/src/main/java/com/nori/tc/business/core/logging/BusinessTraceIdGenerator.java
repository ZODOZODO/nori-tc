package com.nori.tc.business.core.logging;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightweight ULID-based traceId generator for business runtime flows.
 *
 * <p>Design intent:</p>
 * <p>1) Keep traceId generation local to business bounded context.</p>
 * <p>2) Produce gateway-compatible lexical shape(26 chars, Crockford Base32).</p>
 * <p>3) Favor low-latency generation for high-volume subscription paths.</p>
 *
 * <p>Note: this implementation does not enforce strict monotonic ULID ordering
 * for IDs generated in the same millisecond.</p>
 */
public final class BusinessTraceIdGenerator {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private BusinessTraceIdGenerator() {
        // utility class
    }

    /**
     * Generates a new 26-character ULID string.
     *
     * @return generated traceId
     */
    public static String newTraceId() {
        final long nowEpochMillis = System.currentTimeMillis();

        // 10 random bytes(80 bits) complete the ULID payload.
        final byte[] randomness = new byte[10];
        ThreadLocalRandom.current().nextBytes(randomness);

        // ULID binary layout: 6-byte timestamp + 10-byte randomness.
        final byte[] ulidBytes = new byte[16];
        ulidBytes[0] = (byte) (nowEpochMillis >>> 40);
        ulidBytes[1] = (byte) (nowEpochMillis >>> 32);
        ulidBytes[2] = (byte) (nowEpochMillis >>> 24);
        ulidBytes[3] = (byte) (nowEpochMillis >>> 16);
        ulidBytes[4] = (byte) (nowEpochMillis >>> 8);
        ulidBytes[5] = (byte) nowEpochMillis;
        System.arraycopy(randomness, 0, ulidBytes, 6, randomness.length);

        return encodeCrockfordBase32(ulidBytes);
    }

    /**
     * Encodes 128-bit payload as 26-char Crockford Base32.
     */
    private static String encodeCrockfordBase32(final byte[] payload) {
        final char[] out = new char[26];
        int buffer = 0;
        int bitsLeft = 0;
        int outIndex = 0;

        for (byte b : payload) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                final int value = (buffer >>> (bitsLeft - 5)) & 0x1F;
                out[outIndex++] = CROCKFORD[value];
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            final int value = (buffer << (5 - bitsLeft)) & 0x1F;
            out[outIndex++] = CROCKFORD[value];
        }

        while (outIndex < out.length) {
            out[outIndex++] = CROCKFORD[0];
        }
        return new String(out);
    }
}
