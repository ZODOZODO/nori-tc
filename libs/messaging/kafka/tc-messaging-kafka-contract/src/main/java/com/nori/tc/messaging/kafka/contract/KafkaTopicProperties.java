package com.nori.tc.messaging.kafka.contract;

/**
 * 애플리케이션별 Kafka 토픽 이름 제공 계약입니다.
 *
 * <p>각 애플리케이션은 일반적으로 {@code @ConfigurationProperties} 구현체를 통해 이 인터페이스를
 * 구현하며, 토픽 이름을 코드 상수로 하드코딩하지 않도록 강제하는 용도로 사용합니다.</p>
 */
public interface KafkaTopicProperties {

    /**
     * 설비 이벤트 토픽 이름을 반환합니다.
     *
     * @return 설비 이벤트 토픽명
     */
    String getEqpEvents();

    /**
     * UI 이벤트 토픽 이름을 반환합니다.
     *
     * @return UI 이벤트 토픽명
     */
    String getUiEvents();

    /**
     * MES 이벤트 토픽 이름을 반환합니다.
     *
     * @return MES 이벤트 토픽명
     */
    String getMesEvents();

    /**
     * 설비 명령 토픽 이름을 반환합니다.
     *
     * @return 설비 명령 토픽명
     */
    String getEqpCommands();

    /**
     * MES 명령 토픽 이름을 반환합니다.
     *
     * @return MES 명령 토픽명
     */
    String getMesCommands();

    /**
     * UI 명령 토픽 이름을 반환합니다.
     *
     * @return UI 명령 토픽명
     */
    String getUiCommands();
}
