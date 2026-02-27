package com.nori.tc.business.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical observability logs for business runtime flows.
 */
public final class BusinessObservationLogger {

    private static final Logger log = LoggerFactory.getLogger(BusinessObservationLogger.class);
    private static final String UNKNOWN = "N/A";
    private static final int PAYLOAD_PREVIEW_LIMIT = 512;

    private BusinessObservationLogger() {
        // utility class
    }

    public static void logKafkaInboundAccepted(
            final String topic,
            final int partition,
            final long offset,
            final String eqpId,
            final String traceId,
            final String eventType,
            final String source
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        final String eventName = eventName(source, "KAFKA_IN_ACCEPTED");
        log.debug(
                "{}. topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, source={}",
                eventName,
                text(topic),
                partition,
                offset,
                text(eqpId),
                text(traceId),
                text(eventType),
                text(source)
        );
    }

    /**
     * Emits a mailbox enqueue observation with a gateway-style event name pattern.
     *
     * <p>Example event names:
     * {@code BIZ_EQP_RX_MAILBOX_ENQUEUED}, {@code BIZ_MES_RX_MAILBOX_ENQUEUED},
     * {@code BIZ_UI_RX_MAILBOX_ENQUEUED}.</p>
     */
    public static void logRxMailboxEnqueued(
            final String source,
            final String topic,
            final int partition,
            final long offset,
            final String eqpId,
            final String traceId,
            final int queueDepth,
            final int mailboxCount,
            final int readyQueueSize
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        final String eventName = eventName(source, "RX_MAILBOX_ENQUEUED");
        log.debug(
                "{}. topic={}, partition={}, offset={}, eqpId={}, traceId={}, queueDepth={}, mailboxCount={}, readyQueueSize={}",
                eventName,
                text(topic),
                partition,
                offset,
                text(eqpId),
                text(traceId),
                queueDepth,
                mailboxCount,
                readyQueueSize
        );
    }

    /**
     * Emits a canonical receive log line using the gateway-inspired rendering pattern.
     */
    public static void logReceivedMessage(
            final String source,
            final String messageName,
            final String topic,
            final int partition,
            final long offset,
            final String eqpId,
            final String traceId,
            final String payload
    ) {
        log.info(
                "<rcvd {} : {}> topic={}, partition={}, offset={}, eqpId={}, traceId={}, payload={}",
                token(source),
                text(messageName),
                text(topic),
                partition,
                offset,
                text(eqpId),
                text(traceId),
                payloadPreview(payload)
        );
    }

    /**
     * Emits a canonical send log line using the gateway-inspired rendering pattern.
     */
    public static void logSend(
            final String target,
            final String metadata,
            final String data,
            final String topic,
            final String eqpId,
            final String traceId
    ) {
        log.info(
                "<send {} : metadata={} data={}> topic={}, eqpId={}, traceId={}",
                token(target),
                jsonLike(metadata),
                jsonLike(data),
                text(topic),
                text(eqpId),
                text(traceId)
        );
    }

    public static void logTaskStarted(
            final String topic,
            final String eqpId,
            final int partition,
            final long offset,
            final String messageType,
            final String messageName
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "BIZ_TASK_STARTED. topic={}, eqpId={}, partition={}, offset={}, messageType={}, messageName={}",
                text(topic),
                text(eqpId),
                partition,
                offset,
                text(messageType),
                text(messageName)
        );
    }

    public static void logBootReady(
            final String app,
            final String consumeTopics,
            final String produceTopics,
            final String source,
            final int workerThreads,
            final int timeoutThreads
    ) {
        log.info(
                "BIZ_BOOT_READY. app={}, consumeTopics={}, produceTopics={}, source={}, workerThreads={}, timeoutThreads={}",
                text(app),
                text(consumeTopics),
                text(produceTopics),
                text(source),
                workerThreads,
                timeoutThreads
        );
    }

    private static String eventName(final String source, final String suffix) {
        return "BIZ_" + token(source) + "_" + suffix;
    }

    private static String token(final String value) {
        if (value == null || value.isBlank()) {
            return "EVENT";
        }

        final String upper = value.trim().toUpperCase();
        final StringBuilder sanitized = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            final char ch = upper.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                sanitized.append(ch);
                continue;
            }
            sanitized.append('_');
        }
        return sanitized.isEmpty() ? "EVENT" : sanitized.toString();
    }

    private static String payloadPreview(final String payload) {
        if (payload == null || payload.isBlank()) {
            return "{}";
        }
        final String normalized = payload.trim();
        if (normalized.length() <= PAYLOAD_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, PAYLOAD_PREVIEW_LIMIT) + "...(truncated)";
    }

    private static String jsonLike(final String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value.trim();
    }

    private static String text(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? UNKNOWN : trimmed;
    }
}
