package com.nori.tc.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * APP 로그 파일과 EQP 로그 파일의 역할을 명확히 분리하기 위한 Logback 필터입니다.
 *
 * <p>분리 규칙:</p>
 * <p>1) 유효한 {@code eqpId}가 설정된 이벤트는 EQP 전용 로그로만 보냅니다.</p>
 * <p>2) {@code eqpId}가 없거나, 기본값 성격의 {@code unknown} 이벤트는 APP 로그에 남깁니다.</p>
 *
 * <p>즉, APP_FILE appender에서는 "설비 스코프 로그 제외" 역할을 수행합니다.</p>
 */
public final class AppMdcAbsenceFilter extends Filter<ILoggingEvent> {

    /**
     * MDC에서 설비 식별자를 조회할 때 사용할 키입니다.
     */
    private static final String EQP_ID_MDC_KEY = TcMdcKeys.EQP_ID;

    /**
     * SiftingAppender 기본값과 동일한 문자열입니다.
     * <p>실수로 이 값이 설정되어도 APP 로그로 흘려보내도록 분리 규칙을 정의합니다.</p>
     */
    private static final String UNKNOWN_EQP_ID = "unknown";

    /**
     * APP 로그 파일 기록 허용 여부를 판단합니다.
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
        if (!isEqpScopedLog(eqpId)) {
            return FilterReply.NEUTRAL;
        }
        return FilterReply.DENY;
    }

    /**
     * 이벤트가 "설비 스코프 로그"인지 판별합니다.
     *
     * @param eqpId MDC eqpId 값
     * @return 유효한 설비 ID가 존재하면 true
     */
    private static boolean isEqpScopedLog(final String eqpId) {
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
