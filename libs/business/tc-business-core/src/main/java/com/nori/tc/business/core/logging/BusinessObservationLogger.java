package com.nori.tc.business.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical observability logs for business runtime flows.
 */
public final class BusinessObservationLogger {

    private static final Logger log = LoggerFactory.getLogger(BusinessObservationLogger.class);
    private static final String UNKNOWN = "N/A";

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
        log.debug(
                "BIZ_EVENT_KAFKA_IN_ACCEPTED. topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}, source={}",
                text(topic),
                partition,
                offset,
                text(eqpId),
                text(traceId),
                text(eventType),
                text(source)
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

    private static String text(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? UNKNOWN : trimmed;
    }
}
