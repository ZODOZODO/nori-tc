package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.messaging.kafka.starter.contract.KafkaEventMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka DIRECT publisher implementation.
 *
 * - Builds KafkaEventMessage based on ParsedMessage.
 * - Selects topic/key using PublishDecision with fallback to KafkaTopicProperties.
 */
@Component
public class KafkaEventPublisher implements KafkaPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;
    private final GatewayMetrics metrics;

    
    /**
     * 게이트웨이 Kafka 어댑터 구성 요소를 초기화합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param kafkaTemplate 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     * @param topicProperties Kafka 토픽 이름
     * @param metrics 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public KafkaEventPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final KafkaTopicProperties topicProperties,
            final GatewayMetrics metrics
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 메시지 또는 이벤트를 발행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param message 처리할 원본 데이터
     * @param decision 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    @Override
    public void publish(final ParsedMessage message, final PublishDecision decision) throws Exception {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(decision, "decision is null");

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(
                message.equipmentId().value(),
                message.traceId()
        )) {

        // topic은 항상 tc.eqp.events 고정 (명세: Gateway -> Business 이벤트 전용)
        final String topic = topicProperties.getEqpEvents();

        // key는 항상 eqpId 고정 (Kafka shard 계산과 일치)
        final String key = message.equipmentId().value();

        // Invariant: producer key/topic policy is fixed.
        // - topic override is not allowed
        // - key override is not allowed
        if (decision.topic() != null && !decision.topic().equals(topic)) {
            throw new IllegalStateException("Kafka topic override is not allowed for gateway events");
        }
        if (decision.key() != null && !decision.key().equals(key)) {
            throw new IllegalStateException("Kafka key override is not allowed for gateway events");
        }

        final KafkaEventMessage payload = new KafkaEventMessage(
                message.equipmentId().value(),
                message.traceId(),
                message.commInterfaceType().name(),
                message.socketType(),
                message.messageName().value(),
                message.occurredAtEpochMs(),
                message.attributes(),
                message.body()
        );

        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);

        for (Map.Entry<String, String> header : decision.headers().entrySet()) {
            record.headers().add(new RecordHeader(
                    header.getKey(),
                    header.getValue().getBytes(StandardCharsets.UTF_8)
            ));
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("Publishing eqp event to Kafka. topic={}, eqpId={}, traceId={}",
                        topic, key, message.traceId());
            }
            kafkaTemplate.send(record).get();
            metrics.incrementEventPublishSuccess();
            if (log.isDebugEnabled()) {
                log.debug("Kafka event publish success. topic={}, eqpId={}, traceId={}",
                        topic, key, message.traceId());
            }
        } catch (Exception ex) {
            metrics.incrementEventPublishFail();
            throw ex;
        }
        }
    }
}
