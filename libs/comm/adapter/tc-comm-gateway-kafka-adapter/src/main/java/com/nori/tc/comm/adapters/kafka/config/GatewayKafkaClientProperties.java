package com.nori.tc.comm.adapters.kafka.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Gateway 애플리케이션의 Kafka 클라이언트 설정 바인딩 클래스입니다.
 *
 * <p>{@code spring.kafka.*} 설정을 수집하고, Consumer/AdminClient 생성에 필요한
 * 최소 프로퍼티 맵으로 변환합니다.</p>
 */
@ConfigurationProperties(prefix = "spring.kafka")
public class GatewayKafkaClientProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaClientProperties.class);
    private static final String SPRING_JSON_VALUE_DEFAULT_TYPE = "spring.json.value.default.type";

    private String bootstrapServers;
    private final Consumer consumer = new Consumer();
    private final Admin admin = new Admin();

    /**
     * 필수 Kafka 클라이언트 설정값을 검증합니다.
     *
     * <p>운영 중 설정 누락을 늦게 발견하지 않도록 애플리케이션 시작 시점에
     * Fail-Fast로 검증합니다.</p>
     */
    @PostConstruct
    public void validate() {
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

        log.info("GatewayKafkaClientProperties validated. bootstrapServers={}, consumerGroupId={}",
                bootstrapServers, consumer.groupId);
    }

    /**
     * {@code spring.kafka.consumer.*} 기반의 공통 Consumer 프로퍼티를 구성합니다.
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
     * 메시지 value 타입을 고정한 Consumer 프로퍼티를 구성합니다.
     *
     * <p>동일 그룹에서 서로 다른 메시지 타입을 소비할 때
     * listener별 역직렬화 기본 타입을 분리하기 위해 사용합니다.</p>
     */
    public Map<String, Object> buildConsumerProperties(final Class<?> valueType) {
        final Map<String, Object> props = buildConsumerProperties();
        if (valueType != null) {
            props.put(SPRING_JSON_VALUE_DEFAULT_TYPE, valueType.getName());
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer value type pinned. valueType={}", valueType.getName());
            }
        }
        return props;
    }

    /**
     * {@code spring.kafka.*} 기반의 AdminClient 프로퍼티를 구성합니다.
     */
    public Map<String, Object> buildAdminProperties() {
        final Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (admin.properties != null && !admin.properties.isEmpty()) {
            props.putAll(admin.properties);
        }
        return props;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(final String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Admin getAdmin() {
        return admin;
    }

    /**
     * {@code spring.kafka.consumer.*} 하위 설정 바인딩 모델입니다.
     */
    public static final class Consumer {
        private String groupId;
        private String autoOffsetReset;
        private Boolean enableAutoCommit;
        private String keyDeserializer;
        private String valueDeserializer;
        private Map<String, String> properties;

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(final String groupId) {
            this.groupId = groupId;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(final String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        public Boolean getEnableAutoCommit() {
            return enableAutoCommit;
        }

        public void setEnableAutoCommit(final Boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        public String getKeyDeserializer() {
            return keyDeserializer;
        }

        public void setKeyDeserializer(final String keyDeserializer) {
            this.keyDeserializer = keyDeserializer;
        }

        public String getValueDeserializer() {
            return valueDeserializer;
        }

        public void setValueDeserializer(final String valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }

    /**
     * {@code spring.kafka.admin.properties.*} 하위 설정 바인딩 모델입니다.
     */
    public static final class Admin {
        private Map<String, String> properties;

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
