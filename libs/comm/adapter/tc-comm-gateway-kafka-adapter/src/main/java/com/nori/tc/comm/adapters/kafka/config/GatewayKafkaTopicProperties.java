package com.nori.tc.comm.adapters.kafka.config;

import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway Kafka topic 매핑 프로퍼티 바인딩 클래스입니다.
 *
 * <p>{@code tc.messaging.kafka.topic.*} 값을 바인딩하며,
 * 공통 starter 계약인 {@link KafkaTopicProperties}를 구현해
 * 어댑터 전반에서 일관된 topic 참조를 제공합니다.</p>
 */
@ConfigurationProperties(prefix = "tc.messaging.kafka.topic")
public class GatewayKafkaTopicProperties implements KafkaTopicProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaTopicProperties.class);

    /**
     * Gateway -> Business 이벤트 topic입니다.
     */
    private String eqpEvents;

    /**
     * UI Backend -> Gateway 이벤트 topic입니다.
     */
    private String uiEvents;

    /**
     * MES -> Business 이벤트 topic입니다.
     * Gateway에서 직접 사용하지 않더라도 계약 일관성을 위해 보관합니다.
     */
    private String mesEvents;

    /**
     * Business -> Gateway 명령 topic입니다.
     */
    private String eqpCommands;

    /**
     * Business -> MES 명령 topic입니다.
     * Gateway에서 직접 사용하지 않더라도 계약 일관성을 위해 보관합니다.
     */
    private String mesCommands;

    /**
     * Gateway/Business -> UI 명령 topic입니다.
     */
    private String uiCommands;

    /**
     * 필수 topic 프로퍼티 바인딩 여부를 검증합니다.
     */
    @PostConstruct
    public void validate() {
        requireText("tc.messaging.kafka.topic.eqp-events", eqpEvents);
        requireText("tc.messaging.kafka.topic.ui-events", uiEvents);
        requireText("tc.messaging.kafka.topic.mes-events", mesEvents);
        requireText("tc.messaging.kafka.topic.eqp-commands", eqpCommands);
        requireText("tc.messaging.kafka.topic.mes-commands", mesCommands);
        requireText("tc.messaging.kafka.topic.ui-commands", uiCommands);

        log.info("GatewayKafkaTopicProperties validated. eqpEvents={}, uiEvents={}, mesEvents={}, eqpCommands={}, mesCommands={}, uiCommands={}",
                eqpEvents, uiEvents, mesEvents, eqpCommands, mesCommands, uiCommands);
        if (log.isDebugEnabled()) {
            log.debug("Gateway Kafka topic properties bound successfully.");
        }
    }

    @Override
    public String getEqpEvents() {
        return eqpEvents;
    }

    public void setEqpEvents(final String eqpEvents) {
        this.eqpEvents = eqpEvents;
    }

    @Override
    public String getUiEvents() {
        return uiEvents;
    }

    public void setUiEvents(final String uiEvents) {
        this.uiEvents = uiEvents;
    }

    @Override
    public String getMesEvents() {
        return mesEvents;
    }

    public void setMesEvents(final String mesEvents) {
        this.mesEvents = mesEvents;
    }

    @Override
    public String getEqpCommands() {
        return eqpCommands;
    }

    public void setEqpCommands(final String eqpCommands) {
        this.eqpCommands = eqpCommands;
    }

    @Override
    public String getMesCommands() {
        return mesCommands;
    }

    public void setMesCommands(final String mesCommands) {
        this.mesCommands = mesCommands;
    }

    @Override
    public String getUiCommands() {
        return uiCommands;
    }

    public void setUiCommands(final String uiCommands) {
        this.uiCommands = uiCommands;
    }

    /**
     * 문자열 프로퍼티가 비어 있지 않은지 검증합니다.
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
