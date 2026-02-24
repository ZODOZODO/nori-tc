package com.nori.tc.messaging.kafka.contract;

/**
 * Kafka에서 역직렬화된 메시지를 실제 애플리케이션 처리기로 위임하는 공통 디스패처 계약입니다.
 *
 * <p>Gateway/Business/UI Backend 등 각 애플리케이션은 자신이 소비하는 메시지 타입에 맞는
 * 구현체를 제공하여, Kafka 구독 런타임과 도메인 처리 로직을 분리할 수 있습니다.</p>
 *
 * @param <T> 디스패처가 처리할 Kafka 메시지 타입
 */
public interface KafkaMessageDispatcher<T> {

    /**
     * 수신한 메시지를 실제 비즈니스 처리기로 전달합니다.
     *
     * @param message 역직렬화가 완료된 Kafka 메시지 객체
     */
    void dispatch(T message);
}
