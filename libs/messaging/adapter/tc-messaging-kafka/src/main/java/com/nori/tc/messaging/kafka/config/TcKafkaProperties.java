package com.nori.tc.messaging.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tc.messaging.kafka.* 설정 바인딩
 */
@ConfigurationProperties(prefix = "tc.messaging.kafka")
public class TcKafkaProperties {

    /**
     * MessagePublishRequest.topic이 비어있을 때 사용할 기본 토픽
     */
    private String defaultTopic;

    private final Topic topic = new Topic();

    public String getDefaultTopic() {
        return defaultTopic;
    }

    public void setDefaultTopic(final String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

    public Topic getTopic() {
        return topic;
    }

    public String resolveFallbackTopic() {
        if (defaultTopic != null && !defaultTopic.isBlank()) {
            return defaultTopic;
        }
        if (topic.eqpEvents != null && !topic.eqpEvents.isBlank()) {
            return topic.eqpEvents;
        }
        return null;
    }

    public static class Topic {
        private String eqpEvents;
        private String uiEvents;
        private String eqpCommands;
        private String uiCommands;

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
}
