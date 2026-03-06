package com.nori.tc.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * EQP 로그 파일 분리를 강제하기 위한 Logback 필터입니다.
 *
 * <p>설계 의도:</p>
 * <p>1) EQP 전용 appender에는 반드시 {@code MDC eqpId}가 설정된 이벤트만 기록합니다.</p>
 * <p>2) {@code eqpId}가 비어 있는 공통/부팅 로그는 EQP 파일로 흘러가지 않도록 차단합니다.</p>
 * <p>3) 이 필터를 적용하면 {@code gateway-unknown.*.log} 파일이 신규 생성되지 않습니다.</p>
 *
 * <p>동작 규칙:</p>
 * <p>- {@code eqpId}가 null/blank 이면 {@link FilterReply#DENY}</p>
 * <p>- 그 외에는 {@link FilterReply#NEUTRAL} (다른 필터/append 정책으로 계속 진행)</p>
 */
public final class EqpMdcPresenceFilter extends Filter<ILoggingEvent> {

    /**
     * MDC에서 설비 식별자를 읽을 때 사용할 키입니다.
     * <p>tc-common-logging 공통 키 상수를 재사용하여 문자열 오타를 방지합니다.</p>
     */
    private static final String EQP_ID_MDC_KEY = TcMdcKeys.EQP_ID;

    /**
     * SiftingAppender 기본값과 동일한 문자열입니다.
     * <p>기본값 상태는 실제 설비 식별자가 아니므로 EQP 파일 기록 대상에서 제외합니다.</p>
     */
    private static final String UNKNOWN_EQP_ID = "unknown";

    /**
     * Logback 이벤트를 검사하여 EQP 로그 파일 기록 허용 여부를 판단합니다.
     *
     * @param event 현재 로깅 이벤트
     * @return 필터 판단 결과
     */
    @Override
    public FilterReply decide(final ILoggingEvent event) {
        if (event == null) {
            return FilterReply.DENY;
        }

        final String eqpId = event.getMDCPropertyMap().get(EQP_ID_MDC_KEY);
        if (!isValidEqpId(eqpId)) {
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }

    /**
     * EQP 파일 기록이 가능한 유효 설비 ID인지 검증합니다.
     *
     * @param eqpId MDC eqpId 값
     * @return 유효하면 true
     */
    private static boolean isValidEqpId(final String eqpId) {
        if (eqpId == null) {
            return false;
        }
        final String normalized = eqpId.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return !UNKNOWN_EQP_ID.equalsIgnoreCase(normalized);
    }
}

