package com.nori.tc.comm.core.port;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.routing.PublishDecision;

/**
 * Kafka 직접 발행 Port (DIRECT_KAFKA 경로)
 *
 * 주의(무유실 목표와의 관계)
 * - DIRECT_KAFKA는 outbox 대비 손실 가능 구간이 존재할 수 있으므로,
 *   기본값은 OUTBOX, DIRECT_KAFKA는 allow-list 예외로 제한 운영하는 것을 권장합니다.
 */
public interface KafkaPublisherPort {

    
    /**
     * 게이트웨이 Kafka 어댑터 메시지 또는 이벤트를 발행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @param decision 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    void publish(ParsedMessage message, PublishDecision decision) throws Exception;
}
