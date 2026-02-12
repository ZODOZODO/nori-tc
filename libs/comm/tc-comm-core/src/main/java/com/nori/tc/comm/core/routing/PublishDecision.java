package com.nori.tc.comm.core.routing;

import java.util.Map;

/**
 * 발행 결정 결과
 *
 * - mode   : OUTBOX 또는 DIRECT_KAFKA
 * - topic  : Kafka 토픽(직접 발행 또는 outbox의 target topic). null이면 adapter가 기본 토픽 사용 가능
 * - key    : Kafka key(파티셔닝/순차성 목적). null이면 adapter가 기본값 사용 가능
 * - headers: 메시지 헤더(추적/버전 태그 등). null 대신 빈 맵 권장
 */
public record PublishDecision(
        PublishMode mode,
        String topic,
        String key,
        Map<String, String> headers
) {
    public PublishDecision {
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (headers == null) headers = Map.of();
    }

    
    /**
     * 통신 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>포트/유스케이스 규약과 메시지 처리 흐름을 기준으로 동작합니다.</p>
     * @return 통신 코어 모듈 처리 결과
     */
    public static PublishDecision outboxDefault() {
        return new PublishDecision(PublishMode.OUTBOX, null, null, Map.of());
    }
}
