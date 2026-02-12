package com.nori.tc.comm.gateway.domain.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * ULID 생성기(가벼운 구현) - Shared Kernel
 *
 * 사용 목적
 * - traceId, dlqId 등 운영 추적용 식별자 생성
 *
 * 구현 요약
 * - 48-bit timestamp(ms) + 80-bit randomness = 128-bit
 * - Crockford Base32 인코딩으로 26자 문자열 생성
 *
 * 성능/운영 고려
 * - SecureRandom은 강하지만 비용이 더 큽니다.
 * - traceId는 보안 토큰이 아니라 추적/상관관계용이므로 ThreadLocalRandom 기반으로 저지연을 우선합니다.
 *
 * 주의(중요)
 * - 이 구현은 "엄격한 단조 증가(monotonic ULID)"를 보장하지 않습니다.
 * - 동일 ms 내에서 생성된 ULID의 정렬이 중요하면 monotonic 구현 또는 검증된 라이브러리로 교체하십시오.
 */
public final class UlidGenerator {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    
    private UlidGenerator() {}

    /**
     * 신규 ULID를 생성합니다.
     *
     * @return 26자 ULID 문자열
     */
    public static String newUlid() {
        final long timeMs = System.currentTimeMillis();

        // 80-bit randomness (10 bytes)
        final byte[] randomness = new byte[10];
        ThreadLocalRandom.current().nextBytes(randomness);

        // 16 bytes total = 6 bytes time + 10 bytes random
        final byte[] ulidBytes = new byte[16];

        // 48-bit timestamp (big-endian)
        ulidBytes[0] = (byte) (timeMs >>> 40);
        ulidBytes[1] = (byte) (timeMs >>> 32);
        ulidBytes[2] = (byte) (timeMs >>> 24);
        ulidBytes[3] = (byte) (timeMs >>> 16);
        ulidBytes[4] = (byte) (timeMs >>> 8);
        ulidBytes[5] = (byte) (timeMs);

        // randomness
        System.arraycopy(randomness, 0, ulidBytes, 6, 10);

        return encodeCrockfordBase32(ulidBytes);
    }

    /**
     * 128-bit(16 bytes) -> 26 chars Crockford Base32
     *
     * 16 bytes = 128 bits
     * 26 chars * 5 bits = 130 bits
     * - 상위 2비트는 0으로 패딩됩니다.
     */
    private static String encodeCrockfordBase32(final byte[] data) {
        final char[] out = new char[26];

        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;

            while (bitsLeft >= 5) {
                final int val = (buffer >>> (bitsLeft - 5)) & 0x1F;
                out[index++] = CROCKFORD[val];
                bitsLeft -= 5;
            }
        }

        // 남은 비트 처리(패딩)
        if (bitsLeft > 0) {
            final int val = (buffer << (5 - bitsLeft)) & 0x1F;
            out[index++] = CROCKFORD[val];
        }

        // 혹시 부족하면 0으로 채움(이론상 26자 맞춰짐)
        while (index < 26) {
            out[index++] = CROCKFORD[0];
        }

        return new String(out);
    }
}
