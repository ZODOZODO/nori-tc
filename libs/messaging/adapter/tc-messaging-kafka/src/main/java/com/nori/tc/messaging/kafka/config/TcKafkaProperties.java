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

    /**
     * 메시징 Kafka 어댑터에서 필요한 값을 조회합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @return 메시징 Kafka 어댑터 처리 결과
     */
    public String getDefaultTopic() {
        return defaultTopic;
    }

    /**
     * 메시징 Kafka 어댑터 상태/설정 값을 반영합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @param defaultTopic Kafka 토픽 이름
     */
    public void setDefaultTopic(final String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

    /**
     * 메시징 Kafka 어댑터에서 필요한 값을 조회합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @return 메시징 Kafka 어댑터 처리 결과
     */
    public Topic getTopic() {
        return topic;
    }

    /**
     * 메시징 Kafka 어댑터 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @return 메시징 Kafka 어댑터 처리 결과
     */
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

        /**
         * 메시징 Kafka 어댑터에서 필요한 값을 조회합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @return 메시징 Kafka 어댑터 처리 결과
         */
        public String getEqpEvents() {
            return eqpEvents;
        }

        /**
         * 메시징 Kafka 어댑터 상태/설정 값을 반영합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @param eqpEvents 처리할 이벤트 객체
         */
        public void setEqpEvents(final String eqpEvents) {
            this.eqpEvents = eqpEvents;
        }

        /**
         * 메시징 Kafka 어댑터에서 필요한 값을 조회합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @return 메시징 Kafka 어댑터 처리 결과
         */
        public String getUiEvents() {
            return uiEvents;
        }

        /**
         * 메시징 Kafka 어댑터 상태/설정 값을 반영합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @param uiEvents 처리할 이벤트 객체
         */
        public void setUiEvents(final String uiEvents) {
            this.uiEvents = uiEvents;
        }

        /**
         * 메시징 Kafka 어댑터에서 필요한 값을 조회합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @return 메시징 Kafka 어댑터 처리 결과
         */
        public String getEqpCommands() {
            return eqpCommands;
        }

        /**
         * 메시징 Kafka 어댑터 상태/설정 값을 반영합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @param eqpCommands 처리할 요청/명령 객체
         */
        public void setEqpCommands(final String eqpCommands) {
            this.eqpCommands = eqpCommands;
        }

        /**
         * 메시징 Kafka 어댑터에서 필요한 값을 조회합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @return 메시징 Kafka 어댑터 처리 결과
         */
        public String getUiCommands() {
            return uiCommands;
        }

        /**
         * 메시징 Kafka 어댑터 상태/설정 값을 반영합니다.
         *
         * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
         * @param uiCommands 처리할 요청/명령 객체
         */
        public void setUiCommands(final String uiCommands) {
            this.uiCommands = uiCommands;
        }
    }
}
