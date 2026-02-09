package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.apps.commgateway.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka DIRECT 발행 구현체
 */
@Component
public class KafkaEventPublisher implements KafkaPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GatewayKafkaTopicProperties topicProperties;

    public KafkaEventPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final GatewayKafkaTopicProperties topicProperties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
    }

    @Override
    public void publish(final ParsedMessage message, final PublishDecision decision) throws Exception {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(decision, "decision is null");

        final String topic = (decision.topic() == null || decision.topic().isBlank())
                ? topicProperties.getEqpEvents()
                : decision.topic();

        final String key = (decision.key() == null || decision.key().isBlank())
                ? message.equipmentId().value()
                : decision.key();

        final GatewayEventMessage payload = GatewayEventMessage.fromParsed(
                message.equipmentId().value(),
                message.traceNo(),
                message.commInterfaceType().name(),
                message.socketType(),
                message.messageName().value(),
                message.occurredAt(),
                message.attributes(),
                message.body()
        );

        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);

        for (Map.Entry<String, String> header : decision.headers().entrySet()) {
            record.headers().add(new RecordHeader(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8)));
        }

        kafkaTemplate.send(record).get();
    }
}
