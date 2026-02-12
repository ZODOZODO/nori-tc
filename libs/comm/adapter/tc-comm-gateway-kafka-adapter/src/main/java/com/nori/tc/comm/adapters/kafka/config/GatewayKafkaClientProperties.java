package com.nori.tc.comm.adapters.kafka.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 클라이언트 설정 바인딩 (spring.kafka.*).
 *
 * - Spring Boot KafkaProperties가 없는 환경을 대비해 직접 바인딩한다
 * - Consumer/Admin에서 필요한 최소 속성만 추려서 Map으로 제공한다
 */
@ConfigurationProperties(prefix = "spring.kafka")
public class GatewayKafkaClientProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaClientProperties.class);

    /**
     * Kafka bootstrap servers (comma-separated).
     */
    private String bootstrapServers;

    private final Consumer consumer = new Consumer();

    private final Admin admin = new Admin();

    
    /**
     * 게이트웨이 Kafka 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
    @PostConstruct
    public void validate() {
        // 필수 설정 검증 (누락 시 기동 즉시 실패)
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalStateException("spring.kafka.bootstrap-servers is required");
        }
        if (consumer.groupId == null || consumer.groupId.isBlank()) {
            throw new IllegalStateException("spring.kafka.consumer.group-id is required");
        }
        if (consumer.autoOffsetReset == null || consumer.autoOffsetReset.isBlank()) {
            throw new IllegalStateException("spring.kafka.consumer.auto-offset-reset is required");
        }
        if (consumer.enableAutoCommit == null) {
            throw new IllegalStateException("spring.kafka.consumer.enable-auto-commit is required");
        }
        if (consumer.enableAutoCommit) {
            throw new IllegalStateException("spring.kafka.consumer.enable-auto-commit must be false");
        }
        if (consumer.keyDeserializer == null || consumer.keyDeserializer.isBlank()) {
            throw new IllegalStateException("spring.kafka.consumer.key-deserializer is required");
        }
        if (consumer.valueDeserializer == null || consumer.valueDeserializer.isBlank()) {
            throw new IllegalStateException("spring.kafka.consumer.value-deserializer is required");
        }
        if (consumer.properties == null || consumer.properties.isEmpty()) {
            throw new IllegalStateException("spring.kafka.consumer.properties.* is required for JsonDeserializer");
        }
        if (!consumer.properties.containsKey("spring.json.value.default.type")) {
            throw new IllegalStateException("spring.kafka.consumer.properties.spring.json.value.default.type is required");
        }
        log.info("GatewayKafkaClientProperties validated. bootstrapServers={}, consumerGroupId={}",
                bootstrapServers, consumer.groupId);
    }

    /**
     * KafkaConsumer 생성에 필요한 설정 Map을 생성한다.
     */
    public Map<String, Object> buildConsumerProperties() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.enableAutoCommit);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, consumer.keyDeserializer);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, consumer.valueDeserializer);
        if (consumer.properties != null && !consumer.properties.isEmpty()) {
            props.putAll(consumer.properties);
        }
        return props;
    }

    /**
     * AdminClient 생성에 필요한 설정 Map을 생성한다.
     */
    public Map<String, Object> buildAdminProperties() {
        final Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (admin.properties != null && !admin.properties.isEmpty()) {
            props.putAll(admin.properties);
        }
        return props;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param bootstrapServers 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public void setBootstrapServers(final String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public Consumer getConsumer() {
        return consumer;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public Admin getAdmin() {
        return admin;
    }

    /**
     * spring.kafka.consumer.* 하위 설정 바인딩
     */
    public static final class Consumer {
        private String groupId;
        private String autoOffsetReset;
        private Boolean enableAutoCommit;
        private String keyDeserializer;
        private String valueDeserializer;
        private Map<String, String> properties;

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 Kafka 어댑터 처리 결과
         */
        public String getGroupId() {
            return groupId;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param groupId 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
         */
        public void setGroupId(final String groupId) {
            this.groupId = groupId;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 Kafka 어댑터 처리 결과
         */
        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param autoOffsetReset 페이징/조회 범위 조건
         */
        public void setAutoOffsetReset(final String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 처리 성공 여부
         */
        public Boolean getEnableAutoCommit() {
            return enableAutoCommit;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param enableAutoCommit 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
         */
        public void setEnableAutoCommit(final Boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 Kafka 어댑터 처리 결과
         */
        public String getKeyDeserializer() {
            return keyDeserializer;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param keyDeserializer 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
         */
        public void setKeyDeserializer(final String keyDeserializer) {
            this.keyDeserializer = keyDeserializer;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 Kafka 어댑터 처리 결과
         */
        public String getValueDeserializer() {
            return valueDeserializer;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param valueDeserializer 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
         */
        public void setValueDeserializer(final String valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 Kafka 어댑터 처리 결과
         */
        public Map<String, String> getProperties() {
            return properties;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param properties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
         */
        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }

    /**
     * spring.kafka.admin.properties.* 하위 설정 바인딩
     */
    public static final class Admin {
        private Map<String, String> properties;

        
        /**
         * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @return 게이트웨이 Kafka 어댑터 처리 결과
         */
        public Map<String, String> getProperties() {
            return properties;
        }

        
        /**
         * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
         *
         * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
         * @param properties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
         */
        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
