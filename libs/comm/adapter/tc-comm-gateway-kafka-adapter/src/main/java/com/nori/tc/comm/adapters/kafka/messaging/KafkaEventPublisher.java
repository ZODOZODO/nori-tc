package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessEventMessage;
import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessEventMessage.GatewayBusinessEventMetadata;
import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessEventMessage.GatewayBusinessHsmsEventData;
import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessEventMessage.GatewayBusinessHsmsSecs2;
import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessEventMessage.GatewayBusinessSocketEventData;
import com.nori.tc.comm.core.message.ParsedMessage;
import com.nori.tc.comm.core.port.KafkaPublisherPort;
import com.nori.tc.comm.core.routing.PublishDecision;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.hsms.secs.Secs2Message;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.contract.KafkaTopicProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
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
 * Kafka DIRECT publisher implementation.
 *
 * - Builds gateway-business fixed envelope JSON(metadata + data).
 * - Enforces eventType validation before publish.
 * - Selects topic/key using PublishDecision with fallback to KafkaTopicProperties.
 */
@Component
public class KafkaEventPublisher implements KafkaPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private static final String GATEWAY_SOURCE = "TC-COMM-GATEWAY-APP";
    private static final Pattern SOCKET_EVENT_TYPE_PATTERN =
            Pattern.compile("^CMD=([^\\s]+)", Pattern.CASE_INSENSITIVE);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;
    private final GatewayMetrics metrics;

    /**
     * Initializes gateway Kafka publisher components.
     */
    public KafkaEventPublisher(
            final KafkaTemplate<String, Object> kafkaTemplate,
            final KafkaTopicProperties topicProperties,
            final GatewayMetrics metrics
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate is null");
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
    }

    /**
     * Publishes gateway events to Kafka with fixed metadata+data JSON.
     *
     * - If eventType cannot be resolved, logs error and skips publishing.
     */
    @Override
    public void publish(final ParsedMessage message, final PublishDecision decision) throws Exception {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(decision, "decision is null");

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(
                message.equipmentId().value(),
                message.traceId()
        )) {

            // Topic is fixed to eqp-events for gateway -> business flow.
            final String topic = topicProperties.getEqpEvents();

            // Kafka key must be eqpId.
            final String key = message.equipmentId().value();

            // Key/topic override is blocked by gateway invariant.
            if (decision.topic() != null && !decision.topic().equals(topic)) {
                throw new IllegalStateException("Kafka topic override is not allowed for gateway events");
            }
            if (decision.key() != null && !decision.key().equals(key)) {
                throw new IllegalStateException("Kafka key override is not allowed for gateway events");
            }

            final GatewayBusinessEventMessage payload = buildGatewayBusinessPayload(message);
            if (payload == null) {
                // eventType missing is a policy violation: log and drop.
                metrics.incrementEventPublishFail();
                return;
            }

            final ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);

            for (Map.Entry<String, String> header : decision.headers().entrySet()) {
                record.headers().add(new RecordHeader(
                        header.getKey(),
                        header.getValue().getBytes(StandardCharsets.UTF_8)
                ));
            }

            try {
                if (log.isDebugEnabled()) {
                    log.debug("Publishing gateway business event to Kafka. topic={}, eqpId={}, traceId={}, eventType={}",
                            topic, key, message.traceId(), payload.metadata().eventType());
                }
                kafkaTemplate.send(record).get();
                metrics.incrementEventPublishSuccess();
                if (log.isDebugEnabled()) {
                    log.debug("Gateway business event publish success. topic={}, eqpId={}, traceId={}, eventType={}",
                            topic, key, message.traceId(), payload.metadata().eventType());
                }
            } catch (Exception ex) {
                metrics.incrementEventPublishFail();
                throw ex;
            }
        }
    }

    /**
     * Converts ParsedMessage to gateway-business fixed envelope.
     *
     * @param message parsed inbound message
     * @return envelope message, or null when eventType cannot be resolved
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
     * Resolves metadata.eventType by protocol-specific rule.
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
     * Resolves SOCKET raw message text from current parsed context.
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
     * Extracts eventType from SOCKET raw command line.
     *
     * Example: CMD=CHECK_REPLY ... -> CHECK_REPLY
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
     * Extracts eventType from messageName token when messageName starts with CMD=.
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
     * Converts HSMS body to base64 raw message.
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
     * Normalizes text and returns null for blank values.
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
