package com.nori.tc.logging;

import org.slf4j.MDC;

import java.util.Objects;

/**
 * MDC scope helper.
 *
 * Usage:
 * - try-with-resources for safe restore
 * - allows per-scope eqpId / traceId assignment
 */
public final class TcLogContext implements AutoCloseable {

    private final String prevEqpId;
    private final String prevTraceId;
    private final boolean eqpIdChanged;
    private final boolean traceIdChanged;

    /**
     * 로깅 모듈 구성 요소를 초기화합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param prevEqpId 로깅 모듈 처리에 사용하는 입력 값
     * @param prevTraceId 로깅 모듈 처리에 사용하는 입력 값
     * @param eqpIdChanged 로깅 모듈 처리에 사용하는 입력 값
     * @param traceIdChanged 로깅 모듈 처리에 사용하는 입력 값
     */
    private TcLogContext(
            final String prevEqpId,
            final String prevTraceId,
            final boolean eqpIdChanged,
            final boolean traceIdChanged
    ) {
        this.prevEqpId = prevEqpId;
        this.prevTraceId = prevTraceId;
        this.eqpIdChanged = eqpIdChanged;
        this.traceIdChanged = traceIdChanged;
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 로깅 모듈 처리 결과
     */
    public static TcLogContext withEqpId(final String eqpId) {
        return with(eqpId, null);
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param traceId 로깅 모듈 처리에 사용하는 입력 값
     * @return 로깅 모듈 처리 결과
     */
    public static TcLogContext withTraceId(final String traceId) {
        return with(null, traceId);
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param traceId 로깅 모듈 처리에 사용하는 입력 값
     * @return 로깅 모듈 처리 결과
     */
    public static TcLogContext withEqpAndTraceId(final String eqpId, final String traceId) {
        return with(eqpId, traceId);
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param traceId 로깅 모듈 처리에 사용하는 입력 값
     * @return 로깅 모듈 처리 결과
     */
    private static TcLogContext with(final String eqpId, final String traceId) {
        final String prevEqp = MDC.get(TcMdcKeys.EQP_ID);
        final String prevTrace = MDC.get(TcMdcKeys.TRACE_ID);

        boolean eqpChanged = false;
        boolean traceChanged = false;

        if (eqpId != null && !eqpId.isBlank()) {
            MDC.put(TcMdcKeys.EQP_ID, eqpId);
            eqpChanged = true;
        }

        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TcMdcKeys.TRACE_ID, traceId);
            traceChanged = true;
        }

        return new TcLogContext(prevEqp, prevTrace, eqpChanged, traceChanged);
    }

    /**
     * 로깅 모듈 리소스를 정리하고 종료합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void close() {
        if (eqpIdChanged) {
            restore(TcMdcKeys.EQP_ID, prevEqpId);
        }
        if (traceIdChanged) {
            restore(TcMdcKeys.TRACE_ID, prevTraceId);
        }
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param key 메시지 키 또는 분배 키
     * @param value 로깅 모듈 처리에 사용하는 입력 값
     */
    private static void restore(final String key, final String value) {
        Objects.requireNonNull(key, "key is null");
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}

