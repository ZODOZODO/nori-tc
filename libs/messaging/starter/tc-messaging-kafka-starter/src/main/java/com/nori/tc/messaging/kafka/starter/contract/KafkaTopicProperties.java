package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Kafka 토픽 설정 계약 인터페이스입니다.
 *
 * <p>각 애플리케이션은 {@code @ConfigurationProperties} 구현체를 통해
 * 모든 토픽명을 외부 프로퍼티로 주입해야 합니다.</p>
 *
 * <p>주의:</p>
 * <p>- 토픽명을 코드 상수로 하드코딩하지 않습니다.</p>
 * <p>- 앱별 소유 토픽이 다르더라도 계약은 동일하게 유지합니다.</p>
 */
public interface KafkaTopicProperties {

    /**
     * 설비 이벤트 토픽명을 반환합니다.
     */
    String getEqpEvents();

    /**
     * UI 이벤트 토픽명을 반환합니다.
     */
    String getUiEvents();

    /**
     * MES 이벤트 토픽명을 반환합니다.
     */
    String getMesEvents();

    /**
     * 설비 명령 토픽명을 반환합니다.
     */
    String getEqpCommands();

    /**
     * MES 명령 토픽명을 반환합니다.
     */
    String getMesCommands();

    /**
     * UI 명령 토픽명을 반환합니다.
     */
    String getUiCommands();
}
