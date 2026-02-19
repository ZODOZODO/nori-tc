package com.nori.tc.comm.adapters.kafka.config;

import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway Kafka 토픽 프로퍼티 바인딩 클래스입니다.
 *
 * <p>{@code tc.messaging.kafka.topic.*} 설정을 바인딩하며,
 * Kafka Starter의 {@link KafkaTopicProperties} 계약을 구현해 공통 구성에서 재사용됩니다.</p>
 */
@ConfigurationProperties(prefix = "tc.messaging.kafka.topic")
public class GatewayKafkaTopicProperties implements KafkaTopicProperties {

    /**
     * 설정 검증 결과를 기록하는 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaTopicProperties.class);

    /**
     * 설비 이벤트 발행/구독 토픽명입니다.
     */
    private String eqpEvents;

    /**
     * UI 이벤트 발행/구독 토픽명입니다.
     */
    private String uiEvents;

    /**
     * MES 이벤트 발행/구독 토픽명입니다.
     */
    private String mesEvents;

    /**
     * 설비 명령 발행/구독 토픽명입니다.
     */
    private String eqpCommands;

    /**
     * MES 명령 발행/구독 토픽명입니다.
     */
    private String mesCommands;

    /**
     * UI 명령 발행/구독 토픽명입니다.
     */
    private String uiCommands;

    /**
     * 애플리케이션 기동 시 토픽명 설정값 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        requireText("tc.messaging.kafka.topic.eqp-events", eqpEvents);
        requireText("tc.messaging.kafka.topic.ui-events", uiEvents);
        requireText("tc.messaging.kafka.topic.mes-events", mesEvents);
        requireText("tc.messaging.kafka.topic.eqp-commands", eqpCommands);
        requireText("tc.messaging.kafka.topic.mes-commands", mesCommands);
        requireText("tc.messaging.kafka.topic.ui-commands", uiCommands);

        if (log.isDebugEnabled()) {
            log.debug("Gateway Kafka topic properties bound successfully.");
        }
        log.info(
                "GatewayKafkaTopicProperties validated. eqpEvents={}, uiEvents={}, mesEvents={}, eqpCommands={}, mesCommands={}, uiCommands={}",
                eqpEvents,
                uiEvents,
                mesEvents,
                eqpCommands,
                mesCommands,
                uiCommands
        );
    }

    /**
     * 설비 이벤트 토픽명을 반환합니다.
     *
     * @return 설비 이벤트 토픽명
     */
    @Override
    public String getEqpEvents() {
        return eqpEvents;
    }

    /**
     * 설비 이벤트 토픽명을 설정합니다.
     *
     * @param eqpEvents 설비 이벤트 토픽명
     */
    public void setEqpEvents(final String eqpEvents) {
        this.eqpEvents = eqpEvents;
    }

    /**
     * UI 이벤트 토픽명을 반환합니다.
     *
     * @return UI 이벤트 토픽명
     */
    @Override
    public String getUiEvents() {
        return uiEvents;
    }

    /**
     * UI 이벤트 토픽명을 설정합니다.
     *
     * @param uiEvents UI 이벤트 토픽명
     */
    public void setUiEvents(final String uiEvents) {
        this.uiEvents = uiEvents;
    }

    /**
     * MES 이벤트 토픽명을 반환합니다.
     *
     * @return MES 이벤트 토픽명
     */
    @Override
    public String getMesEvents() {
        return mesEvents;
    }

    /**
     * MES 이벤트 토픽명을 설정합니다.
     *
     * @param mesEvents MES 이벤트 토픽명
     */
    public void setMesEvents(final String mesEvents) {
        this.mesEvents = mesEvents;
    }

    /**
     * 설비 명령 토픽명을 반환합니다.
     *
     * @return 설비 명령 토픽명
     */
    @Override
    public String getEqpCommands() {
        return eqpCommands;
    }

    /**
     * 설비 명령 토픽명을 설정합니다.
     *
     * @param eqpCommands 설비 명령 토픽명
     */
    public void setEqpCommands(final String eqpCommands) {
        this.eqpCommands = eqpCommands;
    }

    /**
     * MES 명령 토픽명을 반환합니다.
     *
     * @return MES 명령 토픽명
     */
    @Override
    public String getMesCommands() {
        return mesCommands;
    }

    /**
     * MES 명령 토픽명을 설정합니다.
     *
     * @param mesCommands MES 명령 토픽명
     */
    public void setMesCommands(final String mesCommands) {
        this.mesCommands = mesCommands;
    }

    /**
     * UI 명령 토픽명을 반환합니다.
     *
     * @return UI 명령 토픽명
     */
    @Override
    public String getUiCommands() {
        return uiCommands;
    }

    /**
     * UI 명령 토픽명을 설정합니다.
     *
     * @param uiCommands UI 명령 토픽명
     */
    public void setUiCommands(final String uiCommands) {
        this.uiCommands = uiCommands;
    }

    /**
     * 문자열 프로퍼티의 필수 입력 여부를 검증합니다.
     *
     * @param key 프로퍼티 키
     * @param value 프로퍼티 값
     */
    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Kafka topic property: " + key);
        }
    }
}
