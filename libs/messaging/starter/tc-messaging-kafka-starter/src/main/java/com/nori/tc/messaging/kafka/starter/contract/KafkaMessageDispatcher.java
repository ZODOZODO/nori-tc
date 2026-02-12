package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Kafka 인바운드 메시지를 애플리케이션 처리기로 위임하는 공통 디스패처 계약입니다.
 *
 * <p>각 앱(gateway/business/ui-backend)은 자신이 소비하는 메시지 타입에 맞춰
 * 이 인터페이스를 구현하면 됩니다.</p>
 *
 * @param <T> 소비할 Kafka 메시지 payload 타입
 */
public interface KafkaMessageDispatcher<T> {

    /**
     * 단건 메시지를 실제 비즈니스 처리기로 전달합니다.
     *
     * @param message Kafka에서 역직렬화된 인바운드 payload
     */
    void dispatch(T message);
}
