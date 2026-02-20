package com.nori.tc.business.core.logging;

import com.nori.tc.logging.TcLogContext;
import com.nori.tc.logging.TcMdcTaskDecorator;

/**
 * Business 도메인 전용 MDC(Logback) 스코프 헬퍼입니다.
 *
 * <p>설계 목적:</p>
 * <p>1) 비즈니스 계층 코드가 로깅 구현체(TcLogContext)와 직접 결합되지 않도록 경계를 제공합니다.</p>
 * <p>2) try-with-resources 패턴으로 eqpId/traceId MDC를 안전하게 주입/복구합니다.</p>
 * <p>3) 비동기 작업 전환 시 현재 MDC를 캡처하여 작업 스레드로 전달할 수 있게 합니다.</p>
 */
public final class BusinessLogContext implements AutoCloseable {

    /**
     * 실제 MDC put/remove를 담당하는 공통 로그 컨텍스트 구현체입니다.
     */
    private final TcLogContext delegate;

    /**
     * 내부 생성자입니다.
     *
     * @param delegate 공통 로그 컨텍스트 구현체
     */
    private BusinessLogContext(final TcLogContext delegate) {
        this.delegate = delegate;
    }

    /**
     * 현재 스코프에 eqpId만 MDC로 주입합니다.
     *
     * @param eqpId 설비 식별자
     * @return 닫을 수 있는 스코프 컨텍스트
     */
    public static BusinessLogContext withEqpId(final String eqpId) {
        return new BusinessLogContext(TcLogContext.withEqpId(eqpId));
    }

    /**
     * 현재 스코프에 eqpId와 traceId를 함께 MDC로 주입합니다.
     *
     * @param eqpId 설비 식별자
     * @param traceId 추적 식별자
     * @return 닫을 수 있는 스코프 컨텍스트
     */
    public static BusinessLogContext withEqpAndTraceId(final String eqpId, final String traceId) {
        return new BusinessLogContext(TcLogContext.withEqpAndTraceId(eqpId, traceId));
    }

    /**
     * 현재 스레드의 MDC를 캡처한 뒤, 전달된 Runnable에 동일 MDC를 복구하여 실행합니다.
     *
     * @param task 원본 작업
     * @return MDC 전파가 적용된 래핑 작업
     */
    public static Runnable wrap(final Runnable task) {
        return TcMdcTaskDecorator.wrap(task);
    }

    /**
     * 스코프 종료 시 이전 MDC 상태를 복구합니다.
     */
    @Override
    public void close() {
        delegate.close();
    }
}
