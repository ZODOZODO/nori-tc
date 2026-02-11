package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import com.nori.tc.messaging.kafka.starter.contract.KafkaEventMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
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

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;
    private final GatewayMetrics metrics;

    public KafkaEventPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final KafkaTopicProperties topicProperties,
            final GatewayMetrics metrics
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
    }

    @Override
    public void publish(final ParsedMessage message, final PublishDecision decision) throws Exception {
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
            kafkaTemplate.send(record).get();
            metrics.incrementEventPublishSuccess();
        } catch (Exception ex) {
            metrics.incrementEventPublishFail();
            throw ex;
        }
        }
    }
}
