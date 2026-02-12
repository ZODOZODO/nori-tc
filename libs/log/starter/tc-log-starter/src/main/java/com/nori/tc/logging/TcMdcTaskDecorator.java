package com.nori.tc.logging;

import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;

/**
 * Propagates MDC across async thread boundaries.
 */
public final class TcMdcTaskDecorator {

    /**
     * 로깅 모듈 구성 요소를 초기화합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     */
    private TcMdcTaskDecorator() {
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param delegate 로깅 모듈 처리에 사용하는 입력 값
     * @return 로깅 모듈 처리 결과
     */
    public static Runnable wrap(final Runnable delegate) {
        Objects.requireNonNull(delegate, "delegate is null");

        final Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            final Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                apply(captured);
                delegate.run();
            } finally {
                apply(previous);
            }
        };
    }

    /**
     * 로깅 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>MDC 컨텍스트 전파, 로그 압축/보관 정책, 자동 구성 규칙을 기준으로 처리합니다.</p>
     * @param contextMap 로깅 모듈 처리에 사용하는 입력 값
     */
    private static void apply(final Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }
}

