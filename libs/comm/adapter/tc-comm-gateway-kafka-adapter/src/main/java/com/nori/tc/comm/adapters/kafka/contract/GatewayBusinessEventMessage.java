package com.nori.tc.comm.adapters.kafka.contract;

/**
 * Gateway -> Business Kafka message envelope.
 *
 * <p>
 * JSON contract is fixed to {@code metadata + data} shape.
 * - metadata: routing/trace context
 * - data    : protocol-specific payload (SOCKET or HSMS)
 * </p>
 */
public record GatewayBusinessEventMessage(
        GatewayBusinessEventMetadata metadata,
        GatewayBusinessEventData data
) {
    public GatewayBusinessEventMessage {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
    }

    /**
     * Marker interface for protocol-specific payload blocks.
     */
    public sealed interface GatewayBusinessEventData permits GatewayBusinessSocketEventData, GatewayBusinessHsmsEventData {
    }

    /**
     * Envelope metadata block.
     */
    public record GatewayBusinessEventMetadata(
            String eventType,
            String timestamp,
            String source,
            String traceId
    ) {
        public GatewayBusinessEventMetadata {
            requireText("eventType", eventType);
            requireText("timestamp", timestamp);
            requireText("source", source);
            requireText("traceId", traceId);
        }
    }

    /**
     * SOCKET event payload block.
     */
    public record GatewayBusinessSocketEventData(
            String eqpId,
            String interfaceType,
            String rawMessage
    ) implements GatewayBusinessEventData {
        public GatewayBusinessSocketEventData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
            requireText("rawMessage", rawMessage);
        }
    }

    /**
     * HSMS event payload block.
     */
    public record GatewayBusinessHsmsEventData(
            String transactionId,
            String eqpId,
            String interfaceType,
            GatewayBusinessHsmsSecs2 secs2,
            String rawMessage
    ) implements GatewayBusinessEventData {
        public GatewayBusinessHsmsEventData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
            requirePresent("rawMessage", rawMessage);
            if (secs2 == null) {
                throw new IllegalArgumentException("secs2 is required");
            }
        }
    }

    /**
     * HSMS secs2 sub-block.
     */
    public record GatewayBusinessHsmsSecs2(
            String systemBytes,
            String eventId,
            String rawBody
    ) {
        public GatewayBusinessHsmsSecs2 {
            requirePresent("rawBody", rawBody);
        }
    }

    /**
     * Shared mandatory string validation helper.
     */
    private static void requireText(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    /**
     * Shared mandatory nullable check helper.
     */
    private static void requirePresent(final String fieldName, final String value) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}

