package com.nori.tc.comm.adapters.kafka.publish;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskReplyPublisher;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Gateway -> UI 응답 메시지 발행기입니다.
 *
 * <p>공통 UI 파이프라인이 계산한 처리 결과를
 * {@code tc.ui.commands}로 발행합니다.</p>
 */
@Component
public class GatewayUiCommandKafkaPublisher implements KafkaTaskReplyPublisher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiCommandKafkaPublisher.class);
    private static final String GATEWAY_SOURCE = "TC-COMM-GATEWAY-APP";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GatewayKafkaTopicProperties topicProperties;
    private final GatewayKafkaContractSupport contractSupport;
    private final ClockPort clockPort;

    /**
     * UI 응답 발행에 필요한 의존성을 초기화합니다.
     *
     * @param kafkaTemplate Kafka template
     * @param topicProperties topic 설정
     * @param contractSupport Kafka 계약 검증 지원기
     * @param clockPort 시간 포트
     */
    public GatewayUiCommandKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final GatewayKafkaTopicProperties topicProperties,
            final GatewayKafkaContractSupport contractSupport,
            final ClockPort clockPort
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
    }

    /**
     * 실패 결과를 간단히 발행하는 헬퍼 메서드입니다.
     *
     * @param request 원본 UI 요청
     * @param replyEventType 응답 eventType
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
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
                KafkaTaskResult.fail(errorCode, errorMessage)
        );
    }

    /**
     * UI 처리 결과를 응답 메시지로 생성해 Kafka에 발행합니다.
     *
     * @param request 원본 UI 요청
     * @param replyEventType 응답 eventType
     * @param result 처리 결과
     */
    @Override
    public void publishResult(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final KafkaTaskResult result
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
        final String topic = topicProperties.getUiCommands();
        final String key = request.data().eqpId();

        try {
            contractSupport.validateUiTaskCommandRecord(topic, key, payload);
        } catch (IllegalArgumentException ex) {
            log.error("UI reply publish blocked by contract validation. topic={}, eqpId={}, traceId={}, eventType={}",
                    topic, key, request.metadata().traceId(), replyEventType, ex);
            throw new IllegalStateException("UI reply payload validation failed", ex);
        }

        final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
        KafkaHeaderSupport.addTracingHeaders(
                record,
                metadata.traceId(),
                metadata.eventType(),
                metadata.source()
        );

        if (log.isDebugEnabled()) {
            log.debug("Publishing UI reply. topic={}, eqpId={}, traceId={}, eventType={}, status={}",
                    topic,
                    key,
                    request.metadata().traceId(),
                    replyEventType,
                    result.status());
        }

        try {
            kafkaTemplate.send(record).get();
            if (log.isDebugEnabled()) {
                log.debug("UI reply published. topic={}, eqpId={}, traceId={}, eventType={}, status={}",
                        topic,
                        key,
                        request.metadata().traceId(),
                        replyEventType,
                        result.status());
            }
        } catch (Exception ex) {
            log.error("UI reply publish failed. topic={}, eqpId={}, traceId={}, eventType={}",
                    topic, key, request.metadata().traceId(), replyEventType, ex);
            throw new IllegalStateException("Failed to publish UI reply", ex);
        }
    }

    /**
     * 공백 문자열은 null로 정규화합니다.
     *
     * @param value 원본 문자열
     * @return 정규화 문자열
     */
    private String normalizeNullable(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
