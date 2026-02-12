package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Kafka topic properties contract.
 *
 * Each app must provide a @ConfigurationProperties implementation
 * that supplies the topic values from external properties.
 */
public interface KafkaTopicProperties {

    /**
     * 메시징 스타터 모듈에서 필요한 값을 조회합니다.
     *
     * <p>Spring Boot 자동 구성과 메시징 계약 인터페이스를 기준으로 처리합니다.</p>
     * @return 메시징 스타터 모듈 처리 결과
     */
    String getEqpEvents();

    /**
     * 메시징 스타터 모듈에서 필요한 값을 조회합니다.
     *
     * <p>Spring Boot 자동 구성과 메시징 계약 인터페이스를 기준으로 처리합니다.</p>
     * @return 메시징 스타터 모듈 처리 결과
     */
    String getUiEvents();

    /**
     * 메시징 스타터 모듈에서 필요한 값을 조회합니다.
     *
     * <p>Spring Boot 자동 구성과 메시징 계약 인터페이스를 기준으로 처리합니다.</p>
     * @return 메시징 스타터 모듈 처리 결과
     */
    String getEqpCommands();

    /**
     * 메시징 스타터 모듈에서 필요한 값을 조회합니다.
     *
     * <p>Spring Boot 자동 구성과 메시징 계약 인터페이스를 기준으로 처리합니다.</p>
     * @return 메시징 스타터 모듈 처리 결과
     */
    String getUiCommands();
}
