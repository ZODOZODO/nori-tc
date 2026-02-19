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
 * Gateway Kafka 클라이언트 프로퍼티 바인딩 클래스입니다.
 *
 * <p>{@code spring.kafka.*} 값을 바탕으로
 * consumer/admin 생성에 필요한 설정 맵을 구성합니다.</p>
 */
@ConfigurationProperties(prefix = "spring.kafka")
public class GatewayKafkaClientProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaClientProperties.class);
    private static final String SPRING_JSON_VALUE_DEFAULT_TYPE = "spring.json.value.default.type";

    private String bootstrapServers;
    private final Consumer consumer = new Consumer();
    private final Admin admin = new Admin();

    /**
     * 필수 Kafka 설정값을 애플리케이션 시작 시 검증합니다.
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
     * 기본 consumer 설정 맵을 생성합니다.
     *
     * @return Kafka consumer 설정 맵
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
        if (log.isDebugEnabled()) {
            log.debug("Kafka consumer properties built. bootstrapServers={}, groupId={}, autoOffsetReset={}, propertyCount={}",
                    bootstrapServers,
                    consumer.groupId,
                    consumer.autoOffsetReset,
                    props.size());
        }
        return props;
    }

    /**
     * value 타입을 지정해 consumer 설정 맵을 생성합니다.
     *
     * @param valueType 역직렬화 value 타입
     * @return Kafka consumer 설정 맵
     */
    public Map<String, Object> buildConsumerProperties(final Class<?> valueType) {
        return buildConsumerProperties(valueType, null);
    }

    /**
     * value 타입과 groupId override를 적용해 consumer 설정 맵을 생성합니다.
     *
     * @param valueType 역직렬화 value 타입
     * @param groupIdOverride groupId override 값
     * @return Kafka consumer 설정 맵
     */
    public Map<String, Object> buildConsumerProperties(final Class<?> valueType, final String groupIdOverride) {
        final Map<String, Object> props = buildConsumerProperties();
        if (groupIdOverride != null && !groupIdOverride.isBlank()) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdOverride.trim());
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer groupId overridden. groupId={}", groupIdOverride.trim());
            }
        }
        if (valueType != null) {
            props.put(SPRING_JSON_VALUE_DEFAULT_TYPE, valueType.getName());
            if (log.isDebugEnabled()) {
                log.debug("Kafka consumer value type pinned. valueType={}", valueType.getName());
            }
        }
        return props;
    }

    /**
     * AdminClient 설정 맵을 생성합니다.
     *
     * @return Kafka admin 설정 맵
     */
    public Map<String, Object> buildAdminProperties() {
        final Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        if (admin.properties != null && !admin.properties.isEmpty()) {
            props.putAll(admin.properties);
        }
        if (log.isDebugEnabled()) {
            log.debug("Kafka admin properties built. bootstrapServers={}, propertyCount={}",
                    bootstrapServers, props.size());
        }
        return props;
    }

    /**
     * getBootstrapServers 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    /**
     * setBootstrapServers 기능을 수행합니다.
     *
     * @param bootstrapServers 입력 값
     */

    public void setBootstrapServers(final String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * getConsumer 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * getAdmin 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public Admin getAdmin() {
        return admin;
    }

    /**
     * {@code spring.kafka.consumer.*} 바인딩 모델입니다.
     */
    public static final class Consumer {
        private String groupId;
        private String autoOffsetReset;
        private Boolean enableAutoCommit;
        private String keyDeserializer;
        private String valueDeserializer;
        private Map<String, String> properties;

        /**
         * getGroupId 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getGroupId() {
            return groupId;
        }

        /**
         * setGroupId 기능을 수행합니다.
         *
         * @param groupId 입력 값
         */

        public void setGroupId(final String groupId) {
            this.groupId = groupId;
        }

        /**
         * getAutoOffsetReset 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        /**
         * setAutoOffsetReset 기능을 수행합니다.
         *
         * @param autoOffsetReset 입력 값
         */

        public void setAutoOffsetReset(final String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        /**
         * getEnableAutoCommit 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public Boolean getEnableAutoCommit() {
            return enableAutoCommit;
        }

        /**
         * setEnableAutoCommit 기능을 수행합니다.
         *
         * @param enableAutoCommit 입력 값
         */

        public void setEnableAutoCommit(final Boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        /**
         * getKeyDeserializer 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getKeyDeserializer() {
            return keyDeserializer;
        }

        /**
         * setKeyDeserializer 기능을 수행합니다.
         *
         * @param keyDeserializer 입력 값
         */

        public void setKeyDeserializer(final String keyDeserializer) {
            this.keyDeserializer = keyDeserializer;
        }

        /**
         * getValueDeserializer 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public String getValueDeserializer() {
            return valueDeserializer;
        }

        /**
         * setValueDeserializer 기능을 수행합니다.
         *
         * @param valueDeserializer 입력 값
         */

        public void setValueDeserializer(final String valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
        }

        /**
         * getProperties 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public Map<String, String> getProperties() {
            return properties;
        }

        /**
         * setProperties 기능을 수행합니다.
         *
         * @param properties 입력 값
         */

        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }

    /**
     * {@code spring.kafka.admin.properties.*} 바인딩 모델입니다.
     */
    public static final class Admin {
        private Map<String, String> properties;

        /**
         * getProperties 기능을 수행합니다.
         *
         * @return 처리 결과
         */

        public Map<String, String> getProperties() {
            return properties;
        }

        /**
         * setProperties 기능을 수행합니다.
         *
         * @param properties 입력 값
         */

        public void setProperties(final Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
