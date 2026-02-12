package com.nori.tc.comm.gateway.domain.util;

import java.util.Base64;

/**
 * Base64 인코딩/디코딩 유틸 (Shared Kernel)
 *
 * 계약(통합 tc-comm-gateway 기준)
 * - 외부 저장/전달(예: Redis Streams 등)을 위해 payload는 base64(raw bytes)로 표현할 수 있습니다.
 * - 디코딩 결과 raw bytes는 반드시 상한(maxRawBytes)을 적용해야 합니다.
 *
 * 성능/안정성 고려
 * - base64 문자열이 매우 크면 디코딩 자체가 비용이 큽니다.
 * - 따라서 "대략적인 디코딩 길이"를 먼저 추정해,
 *   명백히 초과하는 입력은 디코딩 전에 차단하도록 구현합니다.
 */
public final class Base64Codec {

    
    private Base64Codec() {}

    /**
     * raw bytes -> base64 문자열
     *
     * @param rawBytes 인코딩할 바이트(Null 불가)
     * @return base64 문자열
     */
    public static String encode(final byte[] rawBytes) {
        if (rawBytes == null) {
            throw new IllegalArgumentException("rawBytes is null");
        }
        return Base64.getEncoder().encodeToString(rawBytes);
    }

    /**
     * base64 문자열 -> raw bytes (상한 적용)
     *
     * 동작
     * 1) base64 길이로 "대략적인 raw 길이"를 먼저 추정하여, 명백히 maxRawBytes를 넘으면 즉시 실패
     * 2) 실제 디코딩 수행
     * 3) 디코딩 결과 길이를 다시 검사하여 maxRawBytes 초과 시 실패
     *
     * @param base64 base64 문자열(Null 불가)
     * @param maxRawBytes 디코딩된 raw bytes 최대 허용 크기(0보다 커야 함)
     * @return 디코딩된 raw bytes
     * @throws IllegalArgumentException base64 형식 오류 또는 제한 초과 시
     */
    public static byte[] decodeWithLimit(final String base64, final int maxRawBytes) {
        // 파싱 단계: 입력 포맷을 해석해 필요한 필드만 안전하게 추출합니다.
        if (base64 == null) {
            throw new IllegalArgumentException("base64 is null");
        }
        if (maxRawBytes <= 0) {
            throw new IllegalArgumentException("maxRawBytes must be > 0");
        }

        // 1) 디코딩 전 길이 추정으로 1차 방어
        final int estimated = estimateDecodedLength(base64);
        if (estimated > maxRawBytes) {
            throw new IllegalArgumentException(
                    "Decoded payload too large (estimated): " + estimated + " > " + maxRawBytes
            );
        }

        // 2) 실제 디코딩
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid base64 payload", ex);
        }

        // 3) 디코딩 결과로 2차 방어
        if (decoded.length > maxRawBytes) {
            throw new IllegalArgumentException(
                    "Decoded payload too large: " + decoded.length + " > " + maxRawBytes
            );
        }

        return decoded;
    }

    /**
     * base64 문자열의 "대략적인" 디코딩(raw) 길이를 계산합니다.
     *
     * 참고
     * - base64는 4 chars -> 3 bytes 변환이 기본입니다.
     * - 패딩 '='의 개수(0~2)에 따라 마지막 바이트 수가 줄어듭니다.
     *
     * 제한
     * - 본 메서드는 "명백히 큰 입력을 사전에 차단"하는 용도입니다.
     * - 최종 판단은 decode 결과 길이로 해야 합니다.
     */
    private static int estimateDecodedLength(final String base64) {
        final int len = base64.length();
        if (len == 0) return 0;

        int padding = 0;
        if (len >= 2 && base64.charAt(len - 1) == '=' && base64.charAt(len - 2) == '=') {
            padding = 2;
        } else if (len >= 1 && base64.charAt(len - 1) == '=') {
            padding = 1;
        }

        // len이 4의 배수가 아닐 수 있으므로 올림 처리(과소추정 방지)
        final int blocks = (len + 3) / 4;
        final int estimated = blocks * 3 - padding;

        return Math.max(estimated, 0);
    }
}
