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

    /**
     * 메시징 Kafka 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @param kafkaTemplate 메시징 Kafka 어댑터 처리에 사용하는 입력 값
     * @param properties 모듈 설정 값 객체
     */
    public KafkaMessagePublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final TcKafkaProperties properties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    /**
     * 메시징 Kafka 어댑터 메시지 흐름을 처리합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @param request 처리할 요청/명령 객체
     */
    @Override
    public void publish(final MessagePublishRequest request) throws Exception {
        Objects.requireNonNull(request, "request is null");

        final String topic = resolveTopic(request.topic());
        final String key = request.key();

        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, request.payload());
        writeHeaders(record.headers(), request.headers());

        kafkaTemplate.send(record).get();
    }

    /**
     * 메시징 Kafka 어댑터 규약에 맞게 데이터를 변환/구성합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @param requestTopic Kafka 토픽 이름
     * @return 메시징 Kafka 어댑터 처리 결과
     */
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

    /**
     * 메시징 Kafka 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>Kafka 레코드 구성, 헤더 직렬화, 토픽 결정 규칙을 기준으로 처리합니다.</p>
     * @param headers 메시지 부가 헤더 정보
     * @param values 메시징 Kafka 어댑터 처리에 사용하는 입력 값
     */
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
