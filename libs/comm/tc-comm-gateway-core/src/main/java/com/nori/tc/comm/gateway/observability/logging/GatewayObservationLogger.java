package com.nori.tc.comm.gateway.observability.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical observability logs for gateway runtime flows.
 *
 * <p>This utility centralizes operator-facing log messages so flow-oriented
 * records stay consistent across adapters/core components.</p>
 */
public final class GatewayObservationLogger {

    private static final Logger log = LoggerFactory.getLogger(GatewayObservationLogger.class);
    private static final String UNKNOWN = "N/A";

    private GatewayObservationLogger() {
        // utility class
    }

    public static void logEqpWireAccepted(
            final String eqpId,
            final String interfaceType,
            final String socketType,
            final String remote,
            final int payloadBytes,
            final boolean bound
    ) {
        /*
         * raw wire 수신 시점은 파싱 전 구간이라 메시지 traceId를 정확히 부여할 수 없습니다.
         * 사용자 요구사항에 따라 traceId 없는 GW_EQP_RX_WIRE_ACCEPTED 로그는 전체 비활성화하고,
         * 운영 관측은 traceId가 있는 <rcvd EQP : ...> 로그로만 확인합니다.
         */
        return;
    }

    public static void logEqpInboundMailboxEnqueued(
            final String eqpId,
            final String traceId,
            final int queueDepth,
            final int payloadBytes
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_EQP_RX_MAILBOX_ENQUEUED. eqpId={}, traceId={}, queueDepth={}, bytes={}",
                text(eqpId),
                text(traceId),
                queueDepth,
                payloadBytes
        );
    }

    public static void logEqpOutboundMailboxEnqueued(
            final String eqpId,
            final String traceId,
            final String source,
            final int queueDepth,
            final int payloadBytes
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_EQP_TX_MAILBOX_ENQUEUED. eqpId={}, traceId={}, source={}, queueDepth={}, bytes={}",
                text(eqpId),
                text(traceId),
                text(source),
                queueDepth,
                payloadBytes
        );
    }

    public static void logEqpParseSummary(
            final String eqpId,
            final String interfaceType,
            final String socketType,
            final int parsedCount
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_EQP_RX_PARSE_SUMMARY. eqpId={}, interfaceType={}, socketType={}, parsedCount={}",
                text(eqpId),
                text(interfaceType),
                text(socketType),
                parsedCount
        );
    }

    public static void logEqpEventLifecycleStart(
            final String eqpId,
            final String traceId,
            final String eventType,
            final String interfaceType,
            final String socketType
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_EQP_EVENT_LIFECYCLE_START. eqpId={}, traceId={}, eventType={}, interfaceType={}, socketType={}",
                text(eqpId),
                text(traceId),
                text(eventType),
                text(interfaceType),
                text(socketType)
        );
    }

    public static void logEqpEventContractValidated(
            final String topic,
            final String eqpId,
            final String traceId,
            final String eventType
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_EQP_EVENT_CONTRACT_VALIDATED. topic={}, eqpId={}, traceId={}, eventType={}, checks=[metadata,key,envelope]",
                text(topic),
                text(eqpId),
                text(traceId),
                text(eventType)
        );
    }

    public static void logCmdKafkaInboundAccepted(
            final String topic,
            final int partition,
            final long offset,
            final String eqpId,
            final String traceId,
            final String eventType
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_CMD_KAFKA_IN_ACCEPTED. topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}",
                text(topic),
                partition,
                offset,
                text(eqpId),
                text(traceId),
                text(eventType)
        );
    }

    public static void logUiKafkaInboundAccepted(
            final String topic,
            final int partition,
            final long offset,
            final String eqpId,
            final String traceId,
            final String eventType
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_UI_KAFKA_IN_ACCEPTED. topic={}, partition={}, offset={}, eqpId={}, traceId={}, eventType={}",
                text(topic),
                partition,
                offset,
                text(eqpId),
                text(traceId),
                text(eventType)
        );
    }

    public static void logUiMailboxEnqueued(
            final String topic,
            final String eqpId,
            final String traceId,
            final String eventType,
            final int mailboxCount,
            final int readyQueueSize
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_UI_MAILBOX_ENQUEUED. topic={}, eqpId={}, traceId={}, eventType={}, mailboxCount={}, readyQueueSize={}",
                text(topic),
                text(eqpId),
                text(traceId),
                text(eventType),
                mailboxCount,
                readyQueueSize
        );
    }

    public static void logUiTaskStarted(
            final String topic,
            final String eqpId,
            final String traceId,
            final String eventType,
            final long enqueuedAtEpochMs
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "GW_UI_TASK_STARTED. topic={}, eqpId={}, traceId={}, eventType={}, enqueuedAtEpochMs={}",
                text(topic),
                text(eqpId),
                text(traceId),
                text(eventType),
                enqueuedAtEpochMs
        );
    }

    public static void logBootReady(
            final String app,
            final String interfaces,
            final String consumerTopics,
            final String producerTopics,
            final int gatewayWorkerThreads,
            final int nettyBossThreads,
            final int nettyWorkerThreads,
            final String ownedPartitions,
            final int inboundQueueCapacity,
            final int outboundQueueCapacity
    ) {
        log.info(
                "GW_BOOT_READY. app={}, interfaces={}, consumeTopics={}, produceTopics={}, gatewayWorkerThreads={}, nettyBossThreads={}, nettyWorkerThreads={}, ownedPartitions={}, inboundQueueCapacity={}, outboundQueueCapacity={}",
                text(app),
                text(interfaces),
                text(consumerTopics),
                text(producerTopics),
                gatewayWorkerThreads,
                nettyBossThreads,
                nettyWorkerThreads,
                text(ownedPartitions),
                inboundQueueCapacity,
                outboundQueueCapacity
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
