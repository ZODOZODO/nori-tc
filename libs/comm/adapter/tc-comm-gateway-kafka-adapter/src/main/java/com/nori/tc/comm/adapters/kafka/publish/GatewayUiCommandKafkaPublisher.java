package com.nori.tc.comm.adapters.kafka.publish;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
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
 * <p>UI task 처리 결과를 {@code tc.ui.commands} 토픽으로 발행합니다.</p>
 *
 * <p>운영 원칙:</p>
 * <p>1) reply 발행은 비동기로 수행하여 worker 스레드 블로킹을 방지합니다.</p>
 * <p>2) payload 계약 검증 실패는 즉시 예외로 처리합니다.</p>
 * <p>3) Kafka 브로커 응답 성공/실패는 콜백에서 로그로 남깁니다.</p>
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
     * UI reply 발행기에 필요한 의존성을 주입합니다.
     *
     * @param kafkaTemplate KafkaTemplate
     * @param topicProperties Kafka topic 프로퍼티
     * @param contractSupport Kafka 계약 검증 지원기
     * @param clockPort 시각 공급 포트
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
     * 실패 응답을 만들어 발행합니다.
     *
     * @param request 원본 UI 요청
     * @param replyEventType 응답 eventType
     * @param errorCode 오류 코드
     * @param errorMessage 오류 메시지
     */
    public void publishFailure(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final String errorCode,
            final String errorMessage
    ) {
        publishResult(request, replyEventType, KafkaTaskResult.fail(errorCode, errorMessage));
    }

    /**
     * UI task 처리 결과를 비동기로 발행합니다.
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
                normalizeRequired(replyEventType, "replyEventType"),
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
            log.error(
                    "UI reply publish blocked by contract validation. topic={}, eqpId={}, traceId={}, eventType={}",
                    topic,
                    key,
                    request.metadata().traceId(),
                    replyEventType,
                    ex
            );
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
            log.debug(
                    "UI reply publish requested. topic={}, eqpId={}, traceId={}, eventType={}, status={}",
                    topic,
                    key,
                    request.metadata().traceId(),
                    replyEventType,
                    result.status()
            );
        }

        try {
            kafkaTemplate.send(record).whenComplete((sendResult, ex) -> {
                if (ex != null) {
                    log.error(
                            "UI reply publish failed asynchronously. topic={}, eqpId={}, traceId={}, eventType={}",
                            topic,
                            key,
                            request.metadata().traceId(),
                            replyEventType,
                            ex
                    );
                    return;
                }

                if (log.isDebugEnabled()) {
                    log.debug(
                            "UI reply published asynchronously. topic={}, eqpId={}, traceId={}, eventType={}, status={}",
                            topic,
                            key,
                            request.metadata().traceId(),
                            replyEventType,
                            result.status()
                    );
                }
            });
        } catch (Exception ex) {
            log.error(
                    "UI reply publish scheduling failed. topic={}, eqpId={}, traceId={}, eventType={}",
                    topic,
                    key,
                    request.metadata().traceId(),
                    replyEventType,
                    ex
            );
            throw new IllegalStateException("Failed to schedule UI reply publish", ex);
        }
    }

    /**
     * null/blank 값을 null로 정규화합니다.
     *
     * @param value 입력 문자열
     * @return 정규화 문자열
     */
    private static String normalizeNullable(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 필수 문자열을 검증합니다.
     *
     * @param value 입력 문자열
     * @param fieldName 필드명
     * @return 정규화 문자열
     */
    private static String normalizeRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
