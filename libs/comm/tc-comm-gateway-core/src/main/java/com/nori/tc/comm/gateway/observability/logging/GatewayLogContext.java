package com.nori.tc.comm.gateway.observability.logging;

import com.nori.tc.logging.TcLogContext;
import com.nori.tc.logging.TcMdcTaskDecorator;

/**
 * Gateway-specific log context facade.
 *
 * This class keeps existing call-sites stable while delegating
 * actual MDC handling to tc-common-logging shared utilities.
 */
public final class GatewayLogContext implements AutoCloseable {

    private final TcLogContext delegate;

    
    /**
     * GatewayLogContext 생성자를 초기화합니다.
     *
     * @param delegate 입력 값
     */

    private GatewayLogContext(final TcLogContext delegate) {
        this.delegate = delegate;
    }

    /**
     * Set only eqpId in MDC for this scope.
     */
    public static GatewayLogContext withEqpId(final String eqpId) {
        return new GatewayLogContext(TcLogContext.withEqpId(eqpId));
    }

    /**
     * Set eqpId and traceId in MDC for this scope.
     */
    public static GatewayLogContext withEqpAndTraceId(final String eqpId, final String traceId) {
        return new GatewayLogContext(TcLogContext.withEqpAndTraceId(eqpId, traceId));
    }

    /**
     * Capture current MDC and restore it in async execution.
     */
    public static Runnable wrap(final Runnable task) {
        return TcMdcTaskDecorator.wrap(task);
    }

    
    /**
     * 게이트웨이 코어 모듈 리소스를 정리하고 종료합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    @Override
    public void close() {
        delegate.close();
    }
}

