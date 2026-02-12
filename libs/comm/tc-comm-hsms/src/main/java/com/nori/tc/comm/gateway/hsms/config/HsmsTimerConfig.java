package com.nori.tc.comm.gateway.hsms.config;

/**
 * HSMS 타이머 설정
 *
 * 배경
 * - HSMS(일반적으로 SEMI E37 기반)에서는 세션 유지/장애 감지를 위해 T3~T8 타이머를 사용합니다.
 * - 설비/벤더별로 권장 값이 다르고, 현장에서는 튜닝이 잦습니다.
 *
 * 이 모듈의 방침
 * - "기본값"은 일반적으로 많이 쓰는 값들을 제공하지만, 반드시 site/eqp별로 조정 가능하도록 앱에서 주입하세요.
 * - 여기서는 타이머를 “정의/보관”만 하고, 실제 스케줄링(주기 tick)은 상위 레이어가 호출합니다.
 */
public record HsmsTimerConfig(
        long t3ReplyTimeoutMs,
        long t5ConnectTimeoutMs,
        long t6ControlTimeoutMs,
        long t7NotSelectedTimeoutMs,
        long t8NetworkInterleaveTimeoutMs
) {
    public HsmsTimerConfig {
        if (t3ReplyTimeoutMs <= 0) throw new IllegalArgumentException("t3ReplyTimeoutMs must be > 0");
        if (t5ConnectTimeoutMs <= 0) throw new IllegalArgumentException("t5ConnectTimeoutMs must be > 0");
        if (t6ControlTimeoutMs <= 0) throw new IllegalArgumentException("t6ControlTimeoutMs must be > 0");
        if (t7NotSelectedTimeoutMs <= 0) throw new IllegalArgumentException("t7NotSelectedTimeoutMs must be > 0");
        if (t8NetworkInterleaveTimeoutMs <= 0) throw new IllegalArgumentException("t8NetworkInterleaveTimeoutMs must be > 0");
    }

    /**
     * 권장 기본값(일반 관행 기반)
     *
     * 주의
     * - 이 값이 “표준 고정값”이라는 의미가 아닙니다.
     * - 실제 설비/현장 네트워크 환경에 맞춰 반드시 조정하십시오.
     */
    public static HsmsTimerConfig recommendedDefaults() {
        // 입력/상태를 확인한 뒤 핵심 로직을 수행하고 결과를 정리합니다.
        return new HsmsTimerConfig(
                45_000, // T3: reply timeout (commonly 45s)
                10_000, // T5: connect separation (commonly 10s)
                5_000,  // T6: control transaction timeout
                10_000, // T7: not-selected timeout
                5_000   // T8: network interleave timeout
        );
    }
}
