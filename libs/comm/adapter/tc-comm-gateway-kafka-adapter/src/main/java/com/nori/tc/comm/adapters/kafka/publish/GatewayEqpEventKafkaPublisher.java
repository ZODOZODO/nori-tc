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
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.contract.KafkaHeaderSupport;
import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
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
                return;
            }

            try {
                contractSupport.validateGatewayBusinessEventRecord(topic, key, payload);
            } catch (IllegalArgumentException ex) {
                metrics.incrementEventPublishFail();
                log.error("Gateway event publish skipped (contract validation failed). topic={}, eqpId={}, traceId={}",
                        topic, key, message.traceId(), ex);
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
                if (log.isDebugEnabled()) {
                    log.debug("Publishing gateway business event. topic={}, eqpId={}, traceId={}, eventType={}",
                            topic, key, message.traceId(), payload.metadata().eventType());
                }
                kafkaTemplate.send(record).get();
                metrics.incrementEventPublishSuccess();
                if (log.isDebugEnabled()) {
                    log.debug("Gateway business event published. topic={}, eqpId={}, traceId={}, eventType={}",
                            topic, key, message.traceId(), payload.metadata().eventType());
                }
            } catch (Exception ex) {
                metrics.incrementEventPublishFail();
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
}
