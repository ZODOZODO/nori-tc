package com.nori.tc.ui.adapters.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * UI Backend Kafka 설정 클래스입니다.
 *
 * <p>역할:</p>
 * <ul>
 *   <li>{@link UiKafkaTopicProperties}, {@link UiKafkaPublishProperties} ConfigurationProperties 활성화</li>
 *   <li>{@code tc.ui.commands} 전용 KafkaListenerContainerFactory 생성
 *       (MANUAL_IMMEDIATE ACK 모드, String 역직렬화)</li>
 * </ul>
 *
 * <p>왜 별도 ContainerFactory를 사용하는가:</p>
 * <p>Spring Boot 기본 {@code kafkaListenerContainerFactory}의 ACK 모드는
 * {@code spring.kafka.listener.ack-mode} 설정에 의존하므로,
 * UI commands 구독자만 MANUAL_IMMEDIATE를 보장하기 위해 전용 factory를 정의합니다.
 * String 역직렬화를 명시하여 Gateway/Business의 JSON 응답 메시지를
 * 안전하게 수신합니다.</p>
 *
 * <p>Phase 7 {@code TcUiBackendAutoConfiguration}에서 {@code @Import} 대상입니다.</p>
 */
@Configuration
@EnableConfigurationProperties({UiKafkaTopicProperties.class, UiKafkaPublishProperties.class})
public class UiKafkaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UiKafkaConfiguration.class);

    /**
     * tc.ui.commands 전용 Kafka Listener Container Factory입니다.
     *
     * <p>설정 항목:</p>
     * <ul>
     *   <li>ACK 모드: MANUAL_IMMEDIATE — 처리 완료 후 명시적으로 offset 커밋</li>
     *   <li>Key 역직렬화: StringDeserializer</li>
     *   <li>Value 역직렬화: StringDeserializer (JSON 문자열 → ObjectMapper로 후처리)</li>
     *   <li>auto.commit: false (MANUAL_IMMEDIATE와 함께 명시 비활성화)</li>
     * </ul>
     *
     * <p>NOTE: bootstrap-servers는 {@code spring.kafka.bootstrap-servers} 프로퍼티에서
     * 주입받습니다. 이 값이 설정되지 않으면 기동에 실패합니다.</p>
     *
     * @param bootstrapServers Kafka 브로커 주소 (spring.kafka.bootstrap-servers)
     * @return MANUAL_IMMEDIATE ACK 모드의 ConcurrentKafkaListenerContainerFactory
     */
    @Bean("uiCommandListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> uiCommandListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers}") final String bootstrapServers,
            final CommonErrorHandler uiCommandCommonErrorHandler
    ) {
        final Map<String, Object> consumerProps = buildCommandsConsumerProperties(bootstrapServers);
        final DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);

        final ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // MANUAL_IMMEDIATE: onMessage 메서드 내에서 Acknowledgment.acknowledge() 호출 시 즉시 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(uiCommandCommonErrorHandler);

        log.info(
                "uiCommandListenerContainerFactory 초기화 완료. "
                        + "bootstrapServers={}, ackMode=MANUAL_IMMEDIATE, errorHandler=DLT",
                bootstrapServers
        );
        return factory;
    }

    /**
     * tc.ui.commands 파싱 실패/인프라 실패 처리를 위한 공통 에러 핸들러입니다.
     *
     * <p>핵심 정책:</p>
     * <ul>
     *   <li>재시도 없음(FixedBackOff=0,0)</li>
     *   <li>실패 레코드는 즉시 DLT로 라우팅</li>
     * </ul>
     *
     * @param kafkaTemplate DLT 발행용 KafkaTemplate
     * @param topicProperties Kafka 토픽 설정
     * @return 공통 에러 핸들러
     */
    @Bean
    public CommonErrorHandler uiCommandCommonErrorHandler(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final UiKafkaTopicProperties topicProperties
    ) {
        final DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(topicProperties.getCommandsDltTopic(), record.partition())
        );
        final DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L));
        log.info("uiCommandCommonErrorHandler 초기화 완료. dltTopic={}, retries=0",
                topicProperties.getCommandsDltTopic());
        return errorHandler;
    }

    /**
     * tc.ui.commands 파싱 실패 메시지 보관용 DLT 토픽을 정의합니다.
     *
     * @param topicProperties DLT 토픽 설정
     * @return DLT 토픽 정의
     */
    @Bean
    public NewTopic uiCommandDltTopic(final UiKafkaTopicProperties topicProperties) {
        final NewTopic topic = TopicBuilder.name(topicProperties.getCommandsDltTopic())
                .partitions(topicProperties.getCommandsDltPartitions())
                .replicas(topicProperties.getCommandsDltReplicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(topicProperties.getCommandsDltRetentionMs()))
                .build();
        log.info("uiCommandDltTopic 정의 완료. topic={}, partitions={}, replicationFactor={}, retentionMs={}",
                topicProperties.getCommandsDltTopic(),
                topicProperties.getCommandsDltPartitions(),
                topicProperties.getCommandsDltReplicationFactor(),
                topicProperties.getCommandsDltRetentionMs());
        return topic;
    }

    /**
     * tc.ui.commands 구독자 전용 Kafka Consumer 프로퍼티 맵을 생성합니다.
     *
     * <p>Key/Value 역직렬화를 StringDeserializer로 명시하여
     * Gateway/Business Core가 발행하는 JSON 응답 문자열을 안전하게 수신합니다.
     * UiCommandKafkaSubscriber가 ObjectMapper로 최종 역직렬화를 수행합니다.</p>
     *
     * @param bootstrapServers Kafka 브로커 주소
     * @return Consumer 설정 프로퍼티 맵
     */
    private static Map<String, Object> buildCommandsConsumerProperties(final String bootstrapServers) {
        final Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // MANUAL_IMMEDIATE와 함께 auto.commit을 명시적으로 비활성화
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }
}
