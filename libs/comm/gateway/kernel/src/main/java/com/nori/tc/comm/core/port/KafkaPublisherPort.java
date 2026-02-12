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

    void publish(ParsedMessage message, PublishDecision decision) throws Exception;
}
