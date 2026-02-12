package com.nori.tc.apps.commgateway.metrics;

import org.slf4j.MDC;

import java.util.Objects;

/**
 * 로그 MDC 컨텍스트 헬퍼.
 *
 * - eqpId/traceId를 MDC에 설정하여 로그 라우팅(설비별 파일)과 패턴 출력에 사용한다
 * - try-with-resources로 사용하면 스레드 컨텍스트를 자동으로 원복한다
 */
public final class GatewayLogContext implements AutoCloseable {

    private final String prevEqpId;
    private final String prevTraceId;
    private final boolean eqpIdChanged;
    private final boolean traceIdChanged;

    private GatewayLogContext(
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
     * eqpId만 MDC에 설정한다.
     */
    public static GatewayLogContext withEqpId(final String eqpId) {
        return with(eqpId, null);
    }

    /**
     * eqpId/traceId를 MDC에 설정한다.
     */
    public static GatewayLogContext withEqpAndTraceId(final String eqpId, final String traceId) {
        return with(eqpId, traceId);
    }

    private static GatewayLogContext with(final String eqpId, final String traceId) {
        final String prevEqp = MDC.get("eqpId");
        final String prevTrace = MDC.get("traceId");

        boolean eqpChanged = false;
        boolean traceChanged = false;

        if (eqpId != null && !eqpId.isBlank()) {
            MDC.put("eqpId", eqpId);
            eqpChanged = true;
        }

        if (traceId != null && !traceId.isBlank()) {
            MDC.put("traceId", traceId);
            traceChanged = true;
        }

        return new GatewayLogContext(prevEqp, prevTrace, eqpChanged, traceChanged);
    }

    @Override
    public void close() {
        if (eqpIdChanged) {
            restore("eqpId", prevEqpId);
        }
        if (traceIdChanged) {
            restore("traceId", prevTraceId);
        }
    }

    private static void restore(final String key, final String value) {
        Objects.requireNonNull(key, "key is null");
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
