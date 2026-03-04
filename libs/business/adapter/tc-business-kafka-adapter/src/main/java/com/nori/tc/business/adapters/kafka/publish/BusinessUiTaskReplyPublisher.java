package com.nori.tc.business.adapters.kafka.publish;

import com.nori.tc.business.adapters.kafka.config.BusinessUiTaskPolicyProperties;
import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.logging.BusinessObservationLogger;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskReplyPublisher;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;
import com.nori.tc.messaging.kafka.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskReplyMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Business UI reply 발행기입니다.
 *
 * <p>{@code tc.ui.commands} 토픽으로 UI 처리 결과를 발행합니다.</p>
 *
 * <p>운영 원칙:</p>
 * <p>1) worker 스레드 블로킹 방지를 위해 비동기 발행을 사용합니다.</p>
 * <p>2) 발행 요청 성공/실패는 콜백 로그로 추적합니다.</p>
 * <p>3) 입력값 검증 실패는 즉시 예외로 처리합니다.</p>
 */
@Component
public class BusinessUiTaskReplyPublisher implements KafkaTaskReplyPublisher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskReplyPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BusinessCoreRuntimeProperties runtimeProperties;
    private final BusinessUiTaskPolicyProperties policyProperties;

    /**
     * 의존성을 주입합니다.
     *
     * @param kafkaTemplate KafkaTemplate
     * @param runtimeProperties Business core 런타임 프로퍼티
     * @param policyProperties UI task 정책 프로퍼티
     */
    public BusinessUiTaskReplyPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final BusinessCoreRuntimeProperties runtimeProperties,
            final BusinessUiTaskPolicyProperties policyProperties
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
        this.policyProperties = Objects.requireNonNull(policyProperties, "policyProperties is null");
    }

    /**
     * UI 처리 결과를 비동기로 발행합니다.
     *
     * @param request 원본 UI 요청
     * @param replyEventType 응답 eventType
     * @param result 처리 결과
     * @throws Exception 입력 검증 실패 시 예외
     */
    @Override
    public void publishResult(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final KafkaTaskResult result
    ) throws Exception {
        Objects.requireNonNull(request, "request is null");
        Objects.requireNonNull(result, "result is null");

        final String topic = runtimeProperties.getKafka().getUiCommandsTopic();
        final String eqpId = normalizeRequired(request.data().eqpId(), "eqpId");
        final String traceId = request.metadata().traceId();

        final KafkaUiTaskMessage.KafkaUiTaskMetadata metadata = new KafkaUiTaskMessage.KafkaUiTaskMetadata(
                normalizeRequired(replyEventType, "replyEventType"),
                Instant.now().toString(),
                policyProperties.getSource(),
                traceId
        );

        final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data = new KafkaUiTaskReplyMessage.KafkaUiTaskReplyData(
                eqpId,
                request.data().interfaceType(),
                result.status().name(),
                normalizeNullable(result.errorMessage()),
                normalizeNullable(result.errorCode())
        );

        final KafkaUiTaskReplyMessage payload = new KafkaUiTaskReplyMessage(metadata, data);
        final ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, eqpId, payload);
        KafkaHeaderSupport.addTracingHeaders(
                producerRecord,
                metadata.traceId(),
                metadata.eventType(),
                metadata.source()
        );
        BusinessObservationLogger.logSend(
                "UI",
                buildMetadataJsonForLog(metadata),
                buildDataJsonForLog(data),
                topic,
                eqpId,
                traceId
        );

        if (log.isDebugEnabled()) {
            log.debug(
                    "Business UI reply publish requested. topic={}, eventType={}, eqpId={}, traceId={}, status={}",
                    topic,
                    replyEventType,
                    eqpId,
                    traceId,
                    result.status()
            );
        }

        try {
            kafkaTemplate.send(producerRecord).whenComplete((sendResult, ex) -> {
                if (ex != null) {
                    log.error(
                            "Business UI reply publish failed asynchronously. topic={}, eventType={}, eqpId={}, traceId={}",
                            topic,
                            replyEventType,
                            eqpId,
                            traceId,
                            ex
                    );
                    return;
                }

                if (log.isDebugEnabled()) {
                    log.debug(
                            "Business UI reply published asynchronously. topic={}, eventType={}, eqpId={}, traceId={}, status={}",
                            topic,
                            replyEventType,
                            eqpId,
                            traceId,
                            result.status()
                    );
                }
            });
        } catch (Exception ex) {
            log.error(
                    "Business UI reply publish scheduling failed. topic={}, eventType={}, eqpId={}, traceId={}",
                    topic,
                    replyEventType,
                    eqpId,
                    traceId,
                    ex
            );
            throw ex;
        }
    }

    /**
     * 필수 문자열을 검증하고 정규화합니다.
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

    /**
     * 선택 문자열을 정규화합니다.
     *
     * @param value 입력 문자열
     * @return null 또는 trim 값
     */
    private static String normalizeNullable(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Builds a compact metadata JSON string for operational send logs.
     */
    private static String buildMetadataJsonForLog(final KafkaUiTaskMessage.KafkaUiTaskMetadata metadata) {
        final StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        appendJsonField(sb, "eventType", metadata == null ? null : metadata.eventType(), false);
        appendJsonField(sb, "traceId", metadata == null ? null : metadata.traceId(), true);
        appendJsonField(sb, "source", metadata == null ? null : metadata.source(), true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builds a compact payload JSON string for operational send logs.
     */
    private static String buildDataJsonForLog(final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data) {
        final StringBuilder sb = new StringBuilder(192);
        sb.append('{');
        appendJsonField(sb, "eqpId", data == null ? null : data.eqpId(), false);
        appendJsonField(sb, "interfaceType", data == null ? null : data.interfaceType(), true);
        appendJsonField(sb, "status", data == null ? null : data.status(), true);
        appendJsonField(sb, "errorCode", data == null ? null : data.errorCode(), true);
        appendJsonField(sb, "errorMessage", data == null ? null : data.errorMsg(), true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Appends one JSON field into a mutable buffer.
     */
    private static void appendJsonField(
            final StringBuilder sb,
            final String key,
            final String value,
            final boolean prependComma
    ) {
        if (prependComma) {
            sb.append(',');
        }
        sb.append('"').append(escapeJson(key)).append("\":");
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"').append(escapeJson(value)).append('"');
    }

    /**
     * Escapes control characters so generated JSON remains parse-safe.
     */
    private static String escapeJson(final String value) {
        if (value == null) {
            return "null";
        }
        final StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\r' -> escaped.append("\\r");
                case '\n' -> escaped.append("\\n");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(ch)) {
                        escaped.append("\\u");
                        escaped.append(String.format("%04X", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
