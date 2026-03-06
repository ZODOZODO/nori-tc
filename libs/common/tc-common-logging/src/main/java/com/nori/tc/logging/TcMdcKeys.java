package com.nori.tc.logging;

/**
 * Shared MDC keys used across TC applications.
 */
public final class TcMdcKeys {

    public static final String TRACE_ID = "traceId";
    public static final String EQP_ID = "eqpId";

    /**
     * 로깅 모듈 구성 요소를 초기화합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     */
    private TcMdcKeys() {
    }
}

