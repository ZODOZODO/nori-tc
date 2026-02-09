package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 토픽 매핑 설정
 * - tc-messaging.properties에서 값을 주입받습니다.
 */
@ConfigurationProperties(prefix = "tc.messaging.kafka.topic")
public class GatewayKafkaTopicProperties {

    private String eqpEvents = "tc.eqp.events";
    private String uiEvents = "tc.ui.events";
    private String eqpCommands = "tc.eqp.commands";
    private String uiCommands = "tc.ui.commands";

    public String getEqpEvents() {
        return eqpEvents;
    }

    public void setEqpEvents(final String eqpEvents) {
        this.eqpEvents = eqpEvents;
    }

    public String getUiEvents() {
        return uiEvents;
    }

    public void setUiEvents(final String uiEvents) {
        this.uiEvents = uiEvents;
    }

    public String getEqpCommands() {
        return eqpCommands;
    }

    public void setEqpCommands(final String eqpCommands) {
        this.eqpCommands = eqpCommands;
    }

    public String getUiCommands() {
        return uiCommands;
    }

    public void setUiCommands(final String uiCommands) {
        this.uiCommands = uiCommands;
    }
}
