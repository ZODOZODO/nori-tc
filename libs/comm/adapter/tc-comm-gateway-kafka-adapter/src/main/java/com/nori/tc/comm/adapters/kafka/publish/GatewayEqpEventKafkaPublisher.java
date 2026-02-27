package com.nori.tc.comm.adapters.kafka.publish;

import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessEventMessage;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessEventMessage.GatewayBusinessEventMetadata;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessEventMessage.GatewayBusinessHsmsEventData;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessEventMessage.GatewayBusinessHsmsSecs2;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessEventMessage.GatewayBusinessSocketEventData;
import com.nori.tc.comm.adapters.kafka.contract.GatewayKafkaContractSupport;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Message;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.metrics.GatewayDisposition;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.contract.KafkaTopicProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gateway -> Business 경로의 EQP 이벤트 Kafka 발행기입니다.
 *
 * <p>발행 규칙:
 * 1) topic은 항상 {@code tc.eqp.events}
 * 2) Kafka key는 항상 {@code eqpId}
 * 3) payload는 {@code metadata + data} envelope 고정 구조
 * 4) 발행 전 공통 계약 검증(eventType/source/key 정책)을 수행</p>
 */
@Component
public class GatewayEqpEventKafkaPublisher implements KafkaPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayEqpEventKafkaPublisher.class);
    private static final String GATEWAY_SOURCE = "TC-COMM-GATEWAY-APP";
    private static final Pattern SOCKET_EVENT_TYPE_PATTERN =
            Pattern.compile("^CMD=([^\\s]+)", Pattern.CASE_INSENSITIVE);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;
    private final GatewayKafkaContractSupport contractSupport;
    private final GatewayMetrics metrics;

    /**
     * Kafka 발행에 필요한 의존성을 초기화합니다.
     *
     * @param kafkaTemplate Kafka template
     * @param topicProperties topic 설정
     * @param contractSupport Kafka 계약 검증 지원기
     * @param metrics gateway 메트릭
     */
    public GatewayEqpEventKafkaPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final KafkaTopicProperties topicProperties,
            final GatewayKafkaContractSupport contractSupport,
            final GatewayMetrics metrics
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.contractSupport = Objects.requireNonNull(contractSupport, "contractSupport is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
    }

    /**
     * 파싱된 장비 메시지를 Kafka 이벤트로 발행합니다.
     *
     * @param message 파싱된 원본 메시지
     * @param decision publish 정책 결정 결과
     * @throws Exception Kafka 전송 실패 시 예외 전파
     */
    @Override
    public void publish(final ParsedMessage message, final PublishDecision decision) throws Exception {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(decision, "decision is null");

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(
                message.equipmentId().value(),
                message.traceId()
        )) {
            final String topic = topicProperties.getEqpEvents();
            final String key = message.equipmentId().value();

            // Gateway는 topic/key override를 허용하지 않습니다.
            if (decision.topic() != null && !decision.topic().equals(topic)) {
                throw new IllegalStateException("Kafka topic override is not allowed for gateway events");
            }
            if (decision.key() != null && !decision.key().equals(key)) {
                throw new IllegalStateException("Kafka key override is not allowed for gateway events");
            }

            final GatewayBusinessEventMessage payload = buildGatewayBusinessPayload(message);
            if (payload == null) {
                metrics.incrementEventPublishFail();
                recordEqpEventDisposition(
                        topic,
                        message,
                        GatewayDisposition.REJECTED,
                        "PAYLOAD_BUILD_FAILED",
                        message.messageName().value()
                );
                return;
            }

            /*
             * 운영 관측 기준 "EQP에서 받은 메시지 시작점" 로그는 traceId가 확보된 이후 시점에서 INFO로 출력합니다.
             * 이 로그를 기준으로 후속 Business 발행 로그를 사람이 순서대로 읽을 수 있도록 정렬합니다.
             */
            logReceivedEqpMessage(message, payload);

            try {
                contractSupport.validateGatewayBusinessEventRecord(topic, key, payload);
            } catch (IllegalArgumentException ex) {
                metrics.incrementEventPublishFail();
                recordEqpEventDisposition(
                        topic,
                        message,
                        GatewayDisposition.REJECTED,
                        "CONTRACT_VALIDATION_FAILED",
                        payload.metadata().eventType()
                );
                log.warn(
                        "<warn Business validate : reason={}> topic={}, eqpId={}, traceId={}, eventType={}",
                        "{\"code\":\"CONTRACT_VALIDATION_FAILED\"}",
                        topic,
                        key,
                        message.traceId(),
                        payload.metadata().eventType(),
                        ex
                );
                return;
            }

            final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
            KafkaHeaderSupport.addTracingHeaders(
                    record,
                    payload.metadata().traceId(),
                    payload.metadata().eventType(),
                    payload.metadata().source()
            );
            KafkaHeaderSupport.copyStringHeaders(record, decision.headers());

            try {
                final long sendStartNano = System.nanoTime();
                logBusinessSendAttempt(topic, key, payload);
                kafkaTemplate.send(record).get();
                metrics.incrementEventPublishSuccess();
                recordEqpEventDisposition(
                        topic,
                        message,
                        GatewayDisposition.ACCEPTED,
                        "KAFKA_PUBLISHED",
                        payload.metadata().eventType()
                );
                logBusinessSendCompleted(
                        topic,
                        key,
                        payload,
                        GatewayDisposition.ACCEPTED,
                        "KAFKA_PUBLISHED",
                        elapsedMillis(sendStartNano)
                );
            } catch (Exception ex) {
                metrics.incrementEventPublishFail();
                recordEqpEventDisposition(
                        topic,
                        message,
                        GatewayDisposition.REJECTED,
                        "KAFKA_PUBLISH_EXCEPTION",
                        payload.metadata().eventType()
                );
                logBusinessSendCompleted(
                        topic,
                        key,
                        payload,
                        GatewayDisposition.REJECTED,
                        "KAFKA_PUBLISH_EXCEPTION",
                        -1L
                );
                throw ex;
            }
        }
    }

    /**
     * 파싱 메시지를 Gateway-Business 고정 envelope로 변환합니다.
     *
     * @param message 파싱된 메시지
     * @return 고정 계약 payload, eventType 미해결 시 null
     */
    private GatewayBusinessEventMessage buildGatewayBusinessPayload(final ParsedMessage message) {
        final String interfaceType = message.commInterfaceType().name();
        final String socketRawMessage = resolveSocketRawMessage(message);
        final String eventType = resolveEventType(message, socketRawMessage);

        if (eventType == null) {
            log.error("Gateway event publish skipped: eventType is missing. eqpId={}, traceId={}, interfaceType={}, messageName={}",
                    message.equipmentId().value(),
                    message.traceId(),
                    interfaceType,
                    message.messageName().value());
            return null;
        }

        final GatewayBusinessEventMetadata metadata = new GatewayBusinessEventMetadata(
                eventType,
                Instant.ofEpochMilli(message.occurredAtEpochMs()).toString(),
                GATEWAY_SOURCE,
                message.traceId()
        );

        if (message.commInterfaceType() == CommInterfaceType.SOCKET) {
            return new GatewayBusinessEventMessage(
                    metadata,
                    new GatewayBusinessSocketEventData(
                            message.equipmentId().value(),
                            interfaceType,
                            socketRawMessage
                    )
            );
        }

        if (message.commInterfaceType() == CommInterfaceType.HSMS) {
            final String rawBodyBase64 = resolveHsmsRawBodyBase64(message.body());
            final String systemBytes = normalizeText(message.attributes().get("systemBytes"));
            final String eventId = normalizeText(message.attributes().get("eventId"));
            final String transactionId = normalizeText(message.attributes().get("transactionId"));

            return new GatewayBusinessEventMessage(
                    metadata,
                    new GatewayBusinessHsmsEventData(
                            transactionId,
                            message.equipmentId().value(),
                            interfaceType,
                            new GatewayBusinessHsmsSecs2(systemBytes, eventId, rawBodyBase64),
                            rawBodyBase64
                    )
            );
        }

        log.error("Gateway event publish skipped: unsupported interfaceType. eqpId={}, traceId={}, interfaceType={}",
                message.equipmentId().value(),
                message.traceId(),
                interfaceType);
        return null;
    }

    /**
     * interfaceType 별 eventType 추출 규칙을 적용합니다.
     *
     * @param message 파싱된 메시지
     * @param socketRawMessage socket 원문
     * @return 정규화된 eventType
     */
    private String resolveEventType(final ParsedMessage message, final String socketRawMessage) {
        if (message.commInterfaceType() == CommInterfaceType.SOCKET) {
            final String fromRaw = extractSocketEventTypeFromRawMessage(socketRawMessage);
            if (fromRaw != null) {
                return fromRaw;
            }
            return extractSocketEventTypeFromMessageName(message.messageName().value());
        }

        if (message.commInterfaceType() == CommInterfaceType.HSMS) {
            return normalizeText(message.messageName().value());
        }

        return normalizeText(message.messageName().value());
    }

    /**
     * socket 파싱 컨텍스트에서 원문 메시지를 복원합니다.
     *
     * @param message 파싱된 메시지
     * @return socket 원문
     */
    private String resolveSocketRawMessage(final ParsedMessage message) {
        final Map<String, String> attributes = message.attributes();

        final String rawLine = normalizeText(attributes.get("rawLine"));
        if (rawLine != null) {
            return rawLine;
        }

        final String rawText = normalizeText(attributes.get("rawText"));
        if (rawText != null) {
            return rawText;
        }

        final String messageName = message.messageName().value();
        if (message.body() instanceof String bodyText && !bodyText.isBlank()) {
            return messageName + " " + bodyText;
        }

        return messageName;
    }

    /**
     * socket 원문에서 eventType(CMD=...)를 추출합니다.
     *
     * @param rawMessage socket 원문
     * @return eventType, 미추출 시 null
     */
    private String extractSocketEventTypeFromRawMessage(final String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }

        final Matcher matcher = SOCKET_EVENT_TYPE_PATTERN.matcher(rawMessage.trim());
        if (!matcher.find()) {
            return null;
        }

        return normalizeText(matcher.group(1));
    }

    /**
     * messageName이 CMD=로 시작할 때 eventType을 추출합니다.
     *
     * @param messageName messageName 값
     * @return eventType, 미추출 시 null
     */
    private String extractSocketEventTypeFromMessageName(final String messageName) {
        if (messageName == null || messageName.isBlank()) {
            return null;
        }
        if (!messageName.regionMatches(true, 0, "CMD=", 0, 4)) {
            return null;
        }

        return normalizeText(messageName.substring(4));
    }

    /**
     * HSMS body를 rawBody(base64) 형식으로 변환합니다.
     *
     * @param body HSMS body 객체
     * @return rawBody 문자열
     */
    private String resolveHsmsRawBodyBase64(final Object body) {
        if (body == null) {
            return "";
        }

        if (body instanceof Secs2Message secs2Message) {
            return Base64.getEncoder().encodeToString(secs2Message.rawBody());
        }

        if (body instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }

        if (body instanceof String textBody) {
            return textBody;
        }

        return Base64.getEncoder().encodeToString(String.valueOf(body).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 문자열을 trim 후 공백 문자열이면 null을 반환합니다.
     *
     * @param value 원본 문자열
     * @return 정규화 문자열
     */
    private String normalizeText(final String value) {
        if (value == null) {
            return null;
        }

        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    /**
     * EQP 수신 시작점 관측 로그를 INFO 레벨로 출력합니다.
     *
     * <p>운영자가 "설비에서 무엇을 받았는지"를 traceId와 함께 바로 확인할 수 있도록
     * 사람이 읽는 형식의 canonical 로그를 사용합니다.</p>
     *
     * @param message 파싱 완료 메시지
     * @param payload Business 발행용 payload
     */
    private void logReceivedEqpMessage(
            final ParsedMessage message,
            final GatewayBusinessEventMessage payload
    ) {
        if (message == null || payload == null) {
            return;
        }
        log.info(
                "<rcvd EQP : {}> eqpId={}, traceId={}, interfaceType={}, socketType={}, payload={}",
                safeLogText(message.messageName() == null ? null : message.messageName().value()),
                safeLogText(message.equipmentId() == null ? null : message.equipmentId().value()),
                safeLogText(message.traceId()),
                safeLogText(message.commInterfaceType() == null ? null : message.commInterfaceType().name()),
                safeLogText(message.socketType()),
                buildBusinessDataJsonForLog(payload.data())
        );
    }

    /**
     * Business Kafka 발행 시도 로그를 INFO 레벨로 출력합니다.
     *
     * @param topic 대상 topic
     * @param key Kafka key(eqpId)
     * @param payload 발행 payload
     */
    private void logBusinessSendAttempt(
            final String topic,
            final String key,
            final GatewayBusinessEventMessage payload
    ) {
        if (payload == null) {
            return;
        }
        log.info(
                "<send Business : metadata={} data={}> topic={}, eqpId={}, traceId={}",
                buildBusinessMetadataJsonForLog(payload.metadata()),
                buildBusinessDataJsonForLog(payload.data()),
                safeLogText(topic),
                safeLogText(key),
                safeLogText(payload.metadata() == null ? null : payload.metadata().traceId())
        );
    }

    /**
     * Business Kafka 발행 완료(성공/실패) 로그를 INFO 레벨로 출력합니다.
     *
     * @param topic 대상 topic
     * @param key Kafka key(eqpId)
     * @param payload 발행 payload
     * @param disposition 발행 결과
     * @param reason 결과 사유
     * @param latencyMs 발행 지연 시간(ms), 미측정 시 음수
     */
    private void logBusinessSendCompleted(
            final String topic,
            final String key,
            final GatewayBusinessEventMessage payload,
            final GatewayDisposition disposition,
            final String reason,
            final long latencyMs
    ) {
        final String eventType = payload == null || payload.metadata() == null ? null : payload.metadata().eventType();
        log.info(
                "<done Business send : result={}> topic={}, eqpId={}, traceId={}, eventType={}, latencyMs={}",
                buildBusinessSendResultJsonForLog(disposition, reason, eventType),
                safeLogText(topic),
                safeLogText(key),
                safeLogText(payload == null || payload.metadata() == null ? null : payload.metadata().traceId()),
                safeLogText(eventType),
                latencyMs
        );
    }

    /**
     * Business metadata 블록을 로그용 JSON 문자열로 요약합니다.
     *
     * @param metadata Business metadata
     * @return 로그 출력용 JSON 문자열
     */
    private String buildBusinessMetadataJsonForLog(final GatewayBusinessEventMetadata metadata) {
        final StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        appendJsonStringField(sb, "eventType", metadata == null ? null : metadata.eventType(), false);
        appendJsonStringField(sb, "traceId", metadata == null ? null : metadata.traceId(), true);
        appendJsonStringField(sb, "source", metadata == null ? null : metadata.source(), true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Business data 블록을 로그용 JSON 문자열로 요약합니다.
     *
     * <p>운영 관측 가독성을 위해 원문 payload 전체를 그대로 싣지 않고 핵심 필드만 미리보기로 남깁니다.</p>
     *
     * @param data Business data 블록
     * @return 로그 출력용 JSON 문자열
     */
    private String buildBusinessDataJsonForLog(final GatewayBusinessEventMessage.GatewayBusinessEventData data) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append('{');

        if (data instanceof GatewayBusinessSocketEventData socketData) {
            appendJsonStringField(sb, "eqpId", socketData.eqpId(), false);
            appendJsonStringField(sb, "interfaceType", socketData.interfaceType(), true);
            appendJsonStringField(sb, "rawMessagePreview", previewForLog(socketData.rawMessage(), 256), true);
            sb.append('}');
            return sb.toString();
        }

        if (data instanceof GatewayBusinessHsmsEventData hsmsData) {
            appendJsonStringField(sb, "eqpId", hsmsData.eqpId(), false);
            appendJsonStringField(sb, "interfaceType", hsmsData.interfaceType(), true);
            appendJsonStringField(sb, "transactionId", hsmsData.transactionId(), true);
            final GatewayBusinessHsmsSecs2 secs2 = hsmsData.secs2();
            appendJsonStringField(sb, "systemBytes", secs2 == null ? null : secs2.systemBytes(), true);
            appendJsonStringField(sb, "eventId", secs2 == null ? null : secs2.eventId(), true);
            appendJsonStringField(sb, "rawMessagePreview", previewForLog(hsmsData.rawMessage(), 256), true);
            appendJsonStringField(sb, "rawBodyPreview", previewForLog(secs2 == null ? null : secs2.rawBody(), 128), true);
            sb.append('}');
            return sb.toString();
        }

        appendJsonStringField(sb, "type", data == null ? null : data.getClass().getSimpleName(), false);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 발행 완료 결과를 로그용 JSON 문자열로 구성합니다.
     *
     * @param disposition 발행 결과
     * @param reason 결과 사유
     * @param eventType 이벤트 타입
     * @return 로그 출력용 JSON 문자열
     */
    private String buildBusinessSendResultJsonForLog(
            final GatewayDisposition disposition,
            final String reason,
            final String eventType
    ) {
        final StringBuilder sb = new StringBuilder(96);
        sb.append('{');
        appendJsonStringField(sb, "disposition", disposition == null ? null : disposition.name(), false);
        appendJsonStringField(sb, "reason", reason, true);
        appendJsonStringField(sb, "eventType", eventType, true);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 단일 줄 로그 가독성을 위해 문자열 미리보기를 생성합니다.
     *
     * @param value 원본 문자열
     * @param limit 최대 길이
     * @return 미리보기 문자열
     */
    private String previewForLog(final String value, final int limit) {
        final String normalized = normalizeText(value);
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
     * @param sb 대상 StringBuilder
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
        if (sb == null) {
            return;
        }
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
     * JSON 문자열 값에서 제어문자를 escape 처리합니다.
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
     * 운영 로그에서 null/blank 값을 사람이 읽기 쉬운 기본값으로 치환합니다.
     *
     * @param value 원본 문자열
     * @return 로그 출력 문자열
     */
    private String safeLogText(final String value) {
        final String normalized = normalizeText(value);
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
     * 설비 이벤트 단위 disposition 표준 로그를 기록합니다.
     *
     * <p>이 메서드는 publisher 내부에서 호출되며 이미 eqpId/traceId MDC 스코프 안에서 실행되므로,
     * 운영자는 `traceId` 하나로 gateway 설비 이벤트 종료 상태를 즉시 검색할 수 있습니다.</p>
     *
     * @param topic       발행 대상 topic
     * @param message     파싱된 설비 이벤트
     * @param disposition 처리 결과(disposition)
     * @param reason      처리 결과 사유
     * @param eventType   로그 표시용 eventType
     */
    private void recordEqpEventDisposition(
            final String topic,
            final ParsedMessage message,
            final GatewayDisposition disposition,
            final String reason,
            final String eventType
    ) {
        if (!log.isTraceEnabled()) {
            return;
        }
        log.trace("GATEWAY_EQP_EVENT_DISPOSITION. flow=EQP_EVENT, disposition={}, reason={}, topic={}, eqpId={}, traceId={}, eventType={}",
                disposition,
                reason,
                topic,
                message.equipmentId().value(),
                message.traceId(),
                normalizeText(eventType));
    }
}
