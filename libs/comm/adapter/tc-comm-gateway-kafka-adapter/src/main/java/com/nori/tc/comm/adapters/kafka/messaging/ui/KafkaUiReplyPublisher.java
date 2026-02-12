package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Gateway -> UI 응답 메시지 Kafka 발행기입니다.
 *
 * <p>UI 요청의 traceId를 유지한 표준 envelope를 생성해
 * {@code tc.ui.commands} 토픽으로 발행합니다.</p>
 */
@Component
public class KafkaUiReplyPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaUiReplyPublisher.class);
    private static final String GATEWAY_SOURCE = "TC-COMM-GATEWAY-APP";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GatewayKafkaTopicProperties topicProperties;
    private final ClockPort clockPort;

    /**
     * UI 응답 발행에 필요한 의존성을 초기화합니다.
     */
    public KafkaUiReplyPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final GatewayKafkaTopicProperties topicProperties,
            final ClockPort clockPort
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
    }

    /**
     * 실패 응답(FAIL)을 간편하게 발행합니다.
     */
    public void publishFailure(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final String errorCode,
            final String errorMessage
    ) {
        publishResult(
                request,
                replyEventType,
                new GatewayUiTaskResult(KafkaUiTaskReplyStatus.FAIL, errorCode, errorMessage)
        );
    }

    /**
     * 처리 결과를 기반으로 UI 응답 메시지를 생성/발행합니다.
     */
    public void publishResult(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final GatewayUiTaskResult result
    ) {
        Objects.requireNonNull(request, "request is null");
        Objects.requireNonNull(result, "result is null");

        final String timestamp = Instant.ofEpochMilli(clockPort.nowEpochMillis()).toString();
        final KafkaUiTaskMessage.KafkaUiTaskMetadata metadata = new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                replyEventType,
                timestamp,
                GATEWAY_SOURCE,
                request.metadata().traceId()
        );

        final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data = new KafkaUiTaskReplyMessage.KafkaUiTaskReplyData(
                request.data().eqpId(),
                request.data().interfaceType(),
                result.status().name(),
                normalizeNullable(result.errorMessage()),
                normalizeNullable(result.errorCode())
        );

        final KafkaUiTaskReplyMessage payload = new KafkaUiTaskReplyMessage(metadata, data);
        final ProducerRecord<String, Object> record = new ProducerRecord<>(
                topicProperties.getUiCommands(),
                request.data().eqpId(),
                payload
        );

        if (log.isDebugEnabled()) {
            log.debug("Publishing UI reply. topic={}, eqpId={}, traceId={}, eventType={}, status={}",
                    topicProperties.getUiCommands(),
                    request.data().eqpId(),
                    request.metadata().traceId(),
                    replyEventType,
                    result.status());
        }
        try {
            kafkaTemplate.send(record).get();
            if (log.isDebugEnabled()) {
                log.debug("UI reply published. topic={}, eqpId={}, traceId={}, eventType={}, status={}",
                        topicProperties.getUiCommands(),
                        request.data().eqpId(),
                        request.metadata().traceId(),
                        replyEventType,
                        result.status());
            }
        } catch (Exception ex) {
            log.error("UI reply publish failed. topic={}, eqpId={}, traceId={}, eventType={}",
                    topicProperties.getUiCommands(),
                    request.data().eqpId(),
                    request.metadata().traceId(),
                    replyEventType,
                    ex);
            throw new IllegalStateException("Failed to publish UI reply", ex);
        }
    }

    private String normalizeNullable(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
