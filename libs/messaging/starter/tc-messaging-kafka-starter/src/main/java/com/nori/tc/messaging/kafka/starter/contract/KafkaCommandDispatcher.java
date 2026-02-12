package com.nori.tc.messaging.kafka.starter.contract;

/**
 * 장비 제어용 커맨드 메시지 디스패처 계약입니다.
 *
 * <p>기존 {@link KafkaCommandMessage} 전용 디스패처를 유지하면서,
 * 내부적으로는 제네릭 디스패처 계약({@link KafkaMessageDispatcher})을 재사용합니다.</p>
 */
public interface KafkaCommandDispatcher extends KafkaMessageDispatcher<KafkaCommandMessage> {
}
