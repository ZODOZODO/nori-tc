package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Dispatcher contract for inbound Kafka command messages.
 *
 * Implementations live in each app so that app-specific validation
 * and routing rules can be applied without modifying the starter.
 */
public interface KafkaCommandDispatcher {

    /**
     * 메시징 스타터 모듈 메시지 흐름을 처리합니다.
     *
     * <p>Spring Boot 자동 구성과 메시징 계약 인터페이스를 기준으로 처리합니다.</p>
     * @param command 처리할 요청/명령 객체
     */
    void dispatch(KafkaCommandMessage command);
}
