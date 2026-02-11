package com.nori.tc.apps.commgateway.config;

import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * 게이트웨이 Kafka 토픽 설정 바인딩.
 *
 * - tc.messaging.kafka.topic.* 프로퍼티를 바인딩한다
 * - kafka starter의 KafkaTopicProperties 계약을 구현한다
 */
@ConfigurationProperties(prefix = "tc.messaging.kafka.topic")
public class GatewayKafkaTopicProperties implements KafkaTopicProperties {

    private String eqpEvents;
    private String uiEvents;
    private String eqpCommands;
    private String uiCommands;

    @PostConstruct
    public void validate() {
        requireText("tc.messaging.kafka.topic.eqp-events", eqpEvents);
        requireText("tc.messaging.kafka.topic.ui-events", uiEvents);
        requireText("tc.messaging.kafka.topic.eqp-commands", eqpCommands);
        requireText("tc.messaging.kafka.topic.ui-commands", uiCommands);
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
    public String getEqpCommands() {
        return eqpCommands;
    }

    public void setEqpCommands(final String eqpCommands) {
        this.eqpCommands = eqpCommands;
    }

    @Override
    public String getUiCommands() {
        return uiCommands;
    }

    public void setUiCommands(final String uiCommands) {
        this.uiCommands = uiCommands;
    }

    private static void requireText(final String key, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Kafka topic property: " + key);
        }
    }
}
