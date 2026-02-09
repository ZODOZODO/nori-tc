package com.nori.tc.messaging.kafka.adapter;

import com.nori.tc.messaging.core.port.MessagePublishRequest;
import com.nori.tc.messaging.core.port.MessagePublisherPort;
import com.nori.tc.messaging.kafka.config.TcKafkaProperties;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka 기반 MessagePublisherPort 구현체
 */
public final class KafkaMessagePublisher implements MessagePublisherPort {

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TcKafkaProperties properties;

    public KafkaMessagePublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final TcKafkaProperties properties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    @Override
    public void publish(final MessagePublishRequest request) throws Exception {
        Objects.requireNonNull(request, "request is null");

        final String topic = resolveTopic(request.topic());
        final String key = request.key();

        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, request.payload());
        writeHeaders(record.headers(), request.headers());

        kafkaTemplate.send(record).get();
    }

    private String resolveTopic(final String requestTopic) {
        if (requestTopic != null && !requestTopic.isBlank()) {
            return requestTopic;
        }

        final String fallback = properties.resolveFallbackTopic();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalArgumentException("Kafka topic is required. Set request.topic or tc.messaging.kafka.default-topic");
        }
        return fallback;
    }

    private static void writeHeaders(final Headers headers, final Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            final String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            final String value = entry.getValue();
            headers.add(key, value == null ? EMPTY_BYTES : value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
