package com.nori.tc.comm.adapters.kafka.publish;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
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

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(key, metadata.traceId())) {
            try {
                contractSupport.validateUiTaskCommandRecord(topic, key, payload);
            } catch (IllegalArgumentException ex) {
                log.warn(
                        "<warn UI validate : reason={}> topic={}, eqpId={}, traceId={}, eventType={}",
                        "{\"code\":\"CONTRACT_VALIDATION_FAILED\"}",
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

            final long sendStartNano = System.nanoTime();
            logUiSendAttempt(topic, key, payload);

            try {
                kafkaTemplate.send(record).whenComplete((sendResult, ex) -> {
                    try (GatewayLogContext callbackContext = GatewayLogContext.withEqpAndTraceId(key, metadata.traceId())) {
                        if (ex != null) {
                            logUiSendCompleted(topic, key, payload, result.status().name(), "KAFKA_PUBLISH_EXCEPTION", -1L);
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

                        logUiSendCompleted(
                                topic,
                                key,
                                payload,
                                result.status().name(),
                                "KAFKA_PUBLISHED",
                                elapsedMillis(sendStartNano)
                        );
                    }
                });
            } catch (Exception ex) {
                logUiSendCompleted(topic, key, payload, result.status().name(), "SCHEDULE_FAILED", -1L);
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
    }

    /**
     * UI Kafka 발행 시도 로그를 INFO 레벨로 출력합니다.
     *
     * @param topic 대상 topic
     * @param key Kafka key(eqpId)
     * @param payload UI reply payload
     */
    private void logUiSendAttempt(
            final String topic,
            final String key,
            final KafkaUiTaskReplyMessage payload
    ) {
        if (payload == null) {
            return;
        }
        log.info(
                "<send UI : metadata={} data={}> topic={}, eqpId={}, traceId={}",
                buildUiMetadataJsonForLog(payload.metadata()),
                buildUiDataJsonForLog(payload.data()),
                safeLogText(topic),
                safeLogText(key),
                safeLogText(payload.metadata() == null ? null : payload.metadata().traceId())
        );
    }

    /**
     * UI Kafka 발행 완료(성공/실패) 로그를 INFO 레벨로 출력합니다.
     *
     * @param topic 대상 topic
     * @param key Kafka key(eqpId)
     * @param payload UI reply payload
     * @param status UI reply status
     * @param reason 결과 사유
     * @param latencyMs 발행 지연 시간(ms), 미측정 시 음수
     */
    private void logUiSendCompleted(
            final String topic,
            final String key,
            final KafkaUiTaskReplyMessage payload,
            final String status,
            final String reason,
            final long latencyMs
    ) {
        log.info(
                "<done UI send : result={}> topic={}, eqpId={}, traceId={}, eventType={}, status={}, latencyMs={}",
                buildUiSendResultJsonForLog(status, reason),
                safeLogText(topic),
                safeLogText(key),
                safeLogText(payload == null || payload.metadata() == null ? null : payload.metadata().traceId()),
                safeLogText(payload == null || payload.metadata() == null ? null : payload.metadata().eventType()),
                safeLogText(status),
                latencyMs
        );
    }

    /**
     * UI reply metadata를 로그용 JSON으로 요약합니다.
     *
     * @param metadata UI reply metadata
     * @return 로그용 JSON 문자열
     */
    private static String buildUiMetadataJsonForLog(final KafkaUiTaskMessage.KafkaUiTaskMetadata metadata) {
        final StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        appendJsonStringField(sb, "eventType", metadata == null ? null : metadata.eventType(), false);
        appendJsonStringField(sb, "traceId", metadata == null ? null : metadata.traceId(), true);
        appendJsonStringField(sb, "source", metadata == null ? null : metadata.source(), true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * UI reply data를 로그용 JSON으로 요약합니다.
     *
     * @param data UI reply data
     * @return 로그용 JSON 문자열
     */
    private static String buildUiDataJsonForLog(final KafkaUiTaskReplyMessage.KafkaUiTaskReplyData data) {
        final StringBuilder sb = new StringBuilder(192);
        sb.append('{');
        appendJsonStringField(sb, "eqpId", data == null ? null : data.eqpId(), false);
        appendJsonStringField(sb, "interfaceType", data == null ? null : data.interfaceType(), true);
        appendJsonStringField(sb, "status", data == null ? null : data.STATUS(), true);
        appendJsonStringField(sb, "errorCode", data == null ? null : data.ERRORCODE(), true);
        appendJsonStringField(sb, "errorMessagePreview",
                previewForLog(data == null ? null : data.ERRORMSG(), 160), true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * UI 발행 완료 결과를 로그용 JSON으로 구성합니다.
     *
     * @param status UI 처리 결과 상태
     * @param reason 발행 결과 사유
     * @return 로그용 JSON 문자열
     */
    private static String buildUiSendResultJsonForLog(final String status, final String reason) {
        final StringBuilder sb = new StringBuilder(80);
        sb.append('{');
        appendJsonStringField(sb, "status", status, false);
        appendJsonStringField(sb, "reason", reason, true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 단일 줄 로그 가독성을 위한 문자열 미리보기를 생성합니다.
     *
     * @param value 원본 문자열
     * @param limit 최대 길이
     * @return 미리보기 문자열
     */
    private static String previewForLog(final String value, final int limit) {
        final String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        if (limit <= 0 || normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...(truncated)";
    }

    /**
     * 로그용 JSON 문자열에 문자열 필드를 추가합니다.
     *
     * @param sb 대상 버퍼
     * @param fieldName 필드명
     * @param value 필드값
     * @param prependComma 앞에 콤마를 붙일지 여부
     */
    private static void appendJsonStringField(
            final StringBuilder sb,
            final String fieldName,
            final String value,
            final boolean prependComma
    ) {
        if (prependComma) {
            sb.append(',');
        }
        sb.append('"').append(escapeJson(fieldName)).append("\":");
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"').append(escapeJson(value)).append('"');
    }

    /**
     * JSON 문자열 값 escape 처리 유틸리티입니다.
     *
     * @param value 원본 문자열
     * @return JSON 안전 문자열
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
                        final String hex = String.format("%04X", (int) ch);
                        escaped.append(hex);
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * 운영 로그 출력용 null/blank 치환 유틸리티입니다.
     *
     * @param value 원본 문자열
     * @return 로그 표시 문자열
     */
    private static String safeLogText(final String value) {
        final String normalized = normalizeNullable(value);
        return normalized == null ? "N/A" : normalized;
    }

    /**
     * nanoTime 기준 경과 시간을 ms로 변환합니다.
     *
     * @param startNano 시작 시각(nanoTime)
     * @return 경과 시간(ms)
     */
    private static long elapsedMillis(final long startNano) {
        if (startNano <= 0L) {
            return -1L;
        }
        return Math.max(0L, (System.nanoTime() - startNano) / 1_000_000L);
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
