package com.nori.tc.comm.gateway.domain.limit;

/**
 * Payload/Buffer 관련 공통 제한값 모음 (Shared Kernel)
 *
 * 목적
 * - 통합 tc-comm-gateway에서 “메시지 유실 없이” 운영하되,
 *   비정상/폭주/쓰레기 데이터로 인한 메모리 폭발을 방지하기 위한 기본 상한을 제공합니다.
 *
 * v5.0 권장안(합의된 정책)
 * - payload(raw bytes) 최대 기본값: 256KB
 * - 초과 시: DLQ로 라우팅 + ACK/XDEL(외부 큐/스트림 사용 시) + eqp quarantine(운영 정책)
 *
 * 주의
 * - 이 값은 "기본값(default)"입니다.
 * - 실제 운영에서는 application.yml(또는 config server)로 상향/하향 조정할 수 있도록
 *   core/app 레이어에서 파라미터로 받는 것을 권장합니다.
 */
public final class PayloadLimits {

    
    /**
     * PayloadLimits 생성자를 초기화합니다.
     *
     */

    private PayloadLimits() {}

    /**
     * raw bytes 최대 허용 크기(기본값): 256KB
     *
     * - HSMS/SOCKET 모두 공통 적용 가능한 상한으로 채택했습니다.
     * - 설비/메시지 특성에 따라 값 조정이 필요하면, 이 상수는 "기본값"으로만 사용하십시오.
     */
    public static final int DEFAULT_MAX_RAW_BYTES = 256 * 1024;

    /**
     * base64 문자열 최대 길이(대략치)를 계산합니다.
     *
     * base64 길이 ≈ 4/3 * rawBytes (패딩 포함 가능)
     * - 본 메서드는 "대략적인" 상한 계산입니다.
     * - 엄밀한 검증은 decode 결과 길이로 최종 판단해야 합니다.
     */
    public static int estimateMaxBase64Length(final int maxRawBytes) {
        if (maxRawBytes <= 0) {
            throw new IllegalArgumentException("maxRawBytes must be > 0");
        }
        // 4/3 * n 을 정수로 올림 처리 + 패딩 여유 4
        return ((maxRawBytes + 2) / 3) * 4 + 4;
    }
}
